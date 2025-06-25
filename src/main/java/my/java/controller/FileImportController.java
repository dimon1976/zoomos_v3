package my.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileImportDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FieldMappingTemplate;
import my.java.model.FileOperation;
import my.java.service.client.ClientService;
import my.java.service.file.detector.CsvParameterDetector;
import my.java.service.file.importer.CsvImportService;
import my.java.service.mapping.FieldMappingService;
import my.java.service.notification.ImportProgressNotifier;
import my.java.repository.FileOperationRepository;
import my.java.util.PathResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Контроллер для импорта файлов
 */
@Controller
@RequestMapping("/clients/{clientId}/import")
@RequiredArgsConstructor
@Slf4j
public class FileImportController {

    private final ClientService clientService;
    private final CsvParameterDetector parameterDetector;
    private final FieldMappingService mappingService;
    private final CsvImportService importService;
    private final FileOperationRepository operationRepository;
    private final ImportProgressNotifier progressNotifier;
    private final PathResolver pathResolver;

    /**
     * Страница загрузки файла
     */
    @GetMapping
    public String showImportForm(@PathVariable Long clientId, Model model,
                                 RedirectAttributes redirectAttributes) {
        log.debug("GET request to show import form for client: {}", clientId);

        return clientService.findClientEntityById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("importDto", new FileImportDto());
                    model.addAttribute("entityTypes", Arrays.asList("Product", "Competitor", "Region"));
                    return "import/upload";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Анализ параметров файла
     */
    @PostMapping("/analyze")
    public String analyzeFile(@PathVariable Long clientId,
                              @RequestParam("file") MultipartFile file,
                              Model model,
                              RedirectAttributes redirectAttributes,
                              HttpSession session) {
        log.debug("POST request to analyze file for client: {}", clientId);

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Файл не выбран");
            return "redirect:/clients/" + clientId + "/import";
        }

        try {
            // Определяем параметры файла
            CsvParameterDetector.CsvParameters params = parameterDetector.detect(file.getInputStream());

            // Загружаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new FileOperationException("Клиент не найден"));

            // Создаем DTO для формы
            FileImportDto importDto = FileImportDto.builder()
                    .clientId(clientId)
                    .encoding(params.getEncoding().name())
                    .delimiter(String.valueOf(params.getDelimiter()))
                    .quoteChar(String.valueOf(params.getQuoteChar()))
                    .hasHeader(params.isHasHeader())
                    .build();

            // Сохраняем файл во временную директорию
            Path tempFile = pathResolver.saveToTempFile(file, "analyze");
            session.setAttribute("uploadedFilePath", tempFile.toString());
            session.setAttribute("uploadedFileName", file.getOriginalFilename());

            model.addAttribute("client", client);
            model.addAttribute("importDto", importDto);
            model.addAttribute("detectedParams", params);
            model.addAttribute("fileName", file.getOriginalFilename());
            model.addAttribute("fileSize", file.getSize());
            model.addAttribute("entityTypes", Arrays.asList("Product", "Competitor", "Region"));

            return "import/configure";

        } catch (IOException e) {
            log.error("Ошибка анализа файла: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка анализа файла: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/import";
        }
    }

    /**
     * Выбор или создание шаблона маппинга
     */
    @PostMapping("/mapping")
    public String configureMappings(@PathVariable Long clientId,
                                    @ModelAttribute FileImportDto importDto,
                                    HttpSession session,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        log.debug("POST request to configure mappings for client: {}", clientId);

        try {
            // Получаем путь к файлу из сессии
            String filePath = (String) session.getAttribute("uploadedFilePath");
            String fileName = (String) session.getAttribute("uploadedFileName");

            if (filePath == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Файл не найден в сессии");
                return "redirect:/clients/" + clientId + "/import";
            }

            // Загружаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new FileOperationException("Клиент не найден"));

            // Получаем заголовки из файла
            Path path = Paths.get(filePath);
            CsvParameterDetector.CsvParameters params = parameterDetector.detect(
                    java.nio.file.Files.newInputStream(path)
            );

            // Получаем доступные шаблоны
            List<FieldMappingTemplate> templates = mappingService
                    .getAvailableTemplates(clientId, importDto.getEntityType());

            // Создаем автоматический маппинг для предпросмотра
            FieldMappingTemplate autoTemplate = mappingService
                    .createAutoMapping(clientId, importDto.getEntityType(), params.getSampleHeaders());

            model.addAttribute("client", client);
            model.addAttribute("importDto", importDto);
            model.addAttribute("templates", templates);
            model.addAttribute("autoTemplate", autoTemplate);
            model.addAttribute("csvHeaders", params.getSampleHeaders());
            model.addAttribute("fileName", fileName);

            return "import/mapping";

        } catch (Exception e) {
            log.error("Ошибка настройки маппинга: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка настройки маппинга: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/import";
        }
    }

    /**
     * Запуск импорта
     */
    @PostMapping("/start")
    public String startImport(@PathVariable Long clientId,
                              @ModelAttribute FileImportDto importDto,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        log.debug("POST request to start import for client: {}", clientId);

        try {
            // Получаем путь к файлу из сессии
            String filePath = (String) session.getAttribute("uploadedFilePath");
            String fileName = (String) session.getAttribute("uploadedFileName");

            if (filePath == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Файл не найден в сессии");
                return "redirect:/clients/" + clientId + "/import";
            }

            Path path = Paths.get(filePath);
            long fileSize = java.nio.file.Files.size(path);

            // Создаем запись операции
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new FileOperationException("Клиент не найден"));

            FileOperation operation = FileOperation.builder()
                    .client(client)
                    .operationType(FileOperation.OperationType.IMPORT)
                    .fileName(fileName)
                    .fileType("CSV")
                    .fileSize(fileSize)
                    .status(FileOperation.OperationStatus.PENDING)
                    .sourceFilePath(filePath)
                    .build();

            operation = operationRepository.save(operation);

            // Запускаем асинхронный импорт напрямую с путем к файлу
            importDto.setClientId(clientId);
            importService.importCsvFromPath(path, importDto, operation);

            // Уведомляем о начале
            progressNotifier.notifyStart(operation.getId());

            // Очищаем сессию
            session.removeAttribute("uploadedFilePath");
            session.removeAttribute("uploadedFileName");

            redirectAttributes.addFlashAttribute("successMessage",
                    "Импорт файла запущен. Вы можете отслеживать прогресс на странице операции.");

            return "redirect:/operations/" + operation.getId() + "/status";

        } catch (Exception e) {
            log.error("Ошибка запуска импорта: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка запуска импорта: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/import";
        }
    }

    /**
     * Сохранение нового шаблона маппинга
     */
    @PostMapping("/mapping/save")
    @ResponseBody
    public String saveMappingTemplate(@PathVariable Long clientId,
                                      @RequestBody FieldMappingTemplate template) {
        log.debug("POST request to save mapping template for client: {}", clientId);

        try {
            // Здесь логика сохранения шаблона
            // Возвращаем ID сохраненного шаблона
            return "{\"templateId\": 1}";
        } catch (Exception e) {
            log.error("Ошибка сохранения шаблона: {}", e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}