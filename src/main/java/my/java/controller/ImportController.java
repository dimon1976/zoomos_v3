package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FieldMappingDto;
import my.java.model.FileOperation;
import my.java.service.client.ClientService;
import my.java.service.mapping.FieldMappingService;
import my.java.service.file.analyzer.CsvFileAnalyzer;
import my.java.util.PathResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.util.List;

/**
 * Контроллер для импорта файлов
 */
@Controller
@RequestMapping("/clients/{clientId}/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final ClientService clientService;
    private final FieldMappingService fieldMappingService;
    private final CsvFileAnalyzer csvFileAnalyzer;
    private final PathResolver pathResolver;

    /**
     * Показать форму импорта
     */
    @GetMapping
    public String showImportForm(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show import form for client: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    // Получаем активные шаблоны для клиента
                    List<FieldMappingDto> mappings = fieldMappingService.getActiveMappingsForClient(clientId, null);

                    model.addAttribute("client", client);
                    model.addAttribute("mappings", mappings);
                    model.addAttribute("importType", "COMBINED"); // По умолчанию составной

                    return "import/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Обработать импорт файла
     */
    @PostMapping
    public String handleImport(@PathVariable Long clientId,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam("importType") String importType,
                               @RequestParam(value = "mappingId", required = false) Long mappingId,
                               RedirectAttributes redirectAttributes) {
        log.debug("POST request to import file for client: {}", clientId);

        // Проверка файла
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите файл для загрузки");
            return "redirect:/clients/" + clientId + "/import";
        }

        // Проверка формата файла
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".csv") && !filename.toLowerCase().endsWith(".xlsx"))) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Поддерживаются только файлы формата CSV и XLSX");
            return "redirect:/clients/" + clientId + "/import";
        }

        try {
            // Сохраняем файл во временную директорию
            Path tempFile = pathResolver.saveToTempFile(file, "import_" + clientId);

            // Если выбран шаблон, используем его параметры
            if (mappingId != null) {
                FieldMappingDto mapping = fieldMappingService.getMappingById(mappingId)
                        .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

                // TODO: Здесь будет вызов сервиса импорта с применением шаблона
                log.info("Starting import with mapping: {}", mapping.getName());

                // Временная заглушка - создаем операцию
                FileOperation operation = FileOperation.builder()
                        .operationType(FileOperation.OperationType.IMPORT)
                        .fileName(filename)
                        .fileType(filename.toLowerCase().endsWith(".csv") ? "CSV" : "XLSX")
                        .status(FileOperation.OperationStatus.PENDING)
                        .fieldMappingId(mappingId)
                        .sourceFilePath(tempFile.toString())
                        .fileSize(file.getSize())
                        .build();

                // TODO: Сохранить операцию и запустить асинхронную обработку

                redirectAttributes.addFlashAttribute("successMessage",
                        "Файл загружен и поставлен в очередь на обработку");
                return "redirect:/clients/" + clientId;

            } else {
                // Если шаблон не выбран, анализируем файл и предлагаем создать новый
                if (filename.toLowerCase().endsWith(".csv")) {
                    var analysisResult = csvFileAnalyzer.analyzeFile(tempFile);

                    redirectAttributes.addFlashAttribute("infoMessage",
                            "Файл проанализирован. Создайте шаблон маппинга для импорта данных.");
                    redirectAttributes.addFlashAttribute("analysisResult", analysisResult);

                    return "redirect:/clients/" + clientId + "/mappings/create";
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Для файлов Excel необходимо сначала создать шаблон маппинга");
                    return "redirect:/clients/" + clientId + "/mappings/create";
                }
            }

        } catch (Exception e) {
            log.error("Error during file import", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при импорте файла: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/import";
        }
    }

    /**
     * AJAX: Получить шаблоны для выбранного типа импорта
     */
    @GetMapping("/mappings/{importType}")
    @ResponseBody
    public List<FieldMappingDto> getMappingsForType(@PathVariable Long clientId,
                                                    @PathVariable String importType) {
        log.debug("AJAX request to get mappings for client: {} and import type: {}", clientId, importType);

        String entityType = "COMBINED".equals(importType) ? "COMBINED" : null;
        return fieldMappingService.getActiveMappingsForClient(clientId, entityType);
    }
}