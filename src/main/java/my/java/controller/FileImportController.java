package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.service.client.ClientService;
import my.java.service.file.importer.FileImportService;
import my.java.service.file.mapping.FieldMappingService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Контроллер для работы с импортом файлов.
 */
@Controller
@RequestMapping("/import")
@RequiredArgsConstructor
@Slf4j
public class FileImportController {

    private final FileImportService fileImportService;
    private final ClientService clientService;
    private final FieldMappingService fieldMappingService;

    // Карта активных операций импорта для отслеживания
    private final Map<Long, CompletableFuture<FileOperationDto>> activeImports = Collections.synchronizedMap(new HashMap<>());

    /**
     * Страница импорта файлов.
     */
    @GetMapping
    public String showImportPage(Model model) {
        log.debug("GET request to show import page");
        return "import/index";
    }

    /**
     * Страница выбора клиента для импорта.
     */
    @GetMapping("/select-client")
    public String showSelectClientPage(Model model) {
        log.debug("GET request to show select client page");
        model.addAttribute("clients", clientService.getAllClients());
        return "import/select-client";
    }

    /**
     * Страница загрузки файла для конкретного клиента.
     */
    @GetMapping("/{clientId}")
    public String showUploadPage(@PathVariable Long clientId, Model model) {
        log.debug("GET request to show upload page for client: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "import/upload";
                })
                .orElseGet(() -> {
                    model.addAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:/import/select-client";
                });
    }

    /**
     * Анализ загруженного файла.
     */
    @PostMapping("/{clientId}/analyze")
    public String analyzeFile(
            @PathVariable Long clientId,
            @RequestParam("file") MultipartFile file,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("POST request to analyze file for client: {}, filename: {}", clientId, file.getOriginalFilename());

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите файл для загрузки");
            return "redirect:/import/" + clientId;
        }

        return clientService.getClientById(clientId)
                .map(client -> {
                    try {
                        // Анализируем файл
                        Map<String, Object> analysis = fileImportService.analyzeFile(file);

                        // Добавляем данные в модель
                        model.addAttribute("client", client);
                        model.addAttribute("analysis", analysis);
                        model.addAttribute("fileName", file.getOriginalFilename());
                        model.addAttribute("fileSize", formatFileSize(file.getSize()));

                        // Получаем доступные маппинги полей
                        String entityType = "product"; // По умолчанию
                        if (analysis.containsKey("entityType")) {
                            entityType = (String) analysis.get("entityType");
                        }

                        List<Map<String, Object>> mappings = fieldMappingService.getAvailableMappingsForClient(clientId, entityType);
                        model.addAttribute("availableMappings", mappings);

                        // Возвращаем страницу настройки импорта
                        return "import/configure";
                    } catch (Exception e) {
                        log.error("Error analyzing file: {}", e.getMessage(), e);
                        redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при анализе файла: " + e.getMessage());
                        return "redirect:/import/" + clientId;
                    }
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:/import/select-client";
                });
    }

    /**
     * Запуск импорта файла.
     */
    @PostMapping("/{clientId}/import")
    public String importFile(
            @PathVariable Long clientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mappingId", required = false) Long mappingId,
            @RequestParam(value = "strategyId", required = false) Long strategyId,
            @RequestParam(value = "entityType", required = false, defaultValue = "product") String entityType,
            @RequestParam Map<String, String> params,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("POST request to import file for client: {}, filename: {}", clientId, file.getOriginalFilename());

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите файл для загрузки");
            return "redirect:/import/" + clientId;
        }

        // Получаем клиента из БД
        Client client = clientService.findClientEntityById(clientId)
                .orElseThrow(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return new FileOperationException("Клиент с ID " + clientId + " не найден");
                });

        try {
            // Создаем карту параметров для импорта
            Map<String, String> importParams = new HashMap<>();
            importParams.put("entityType", entityType);

            // Добавляем дополнительные параметры
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!entry.getKey().equals("file") && !entry.getKey().equals("mappingId") &&
                        !entry.getKey().equals("strategyId") && !entry.getKey().equals("entityType")) {
                    importParams.put(entry.getKey(), entry.getValue());
                }
            }

            // Запускаем асинхронный импорт
            CompletableFuture<FileOperationDto> future = fileImportService.importFileAsync(
                    file, client, mappingId, strategyId, importParams);

            // Получаем промежуточный результат
            FileOperationDto operation = future.getNow(null);

            if (operation != null) {
                // Сохраняем задачу в карте активных импортов
                activeImports.put(operation.getId(), future);

                // Перенаправляем на страницу отслеживания прогресса
                redirectAttributes.addFlashAttribute("successMessage",
                        "Импорт файла запущен. Используйте эту страницу для отслеживания прогресса.");
                return "redirect:/import/status/" + operation.getId();
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Не удалось запустить импорт файла");
                return "redirect:/import/" + clientId;
            }
        } catch (Exception e) {
            log.error("Error importing file: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при импорте файла: " + e.getMessage());
            return "redirect:/import/" + clientId;
        }
    }

    /**
     * Страница отслеживания статуса импорта.
     */
    @GetMapping("/status/{operationId}")
    public String showImportStatus(@PathVariable Long operationId, Model model) {
        log.debug("GET request to show import status for operation: {}", operationId);

        try {
            // Получаем текущий статус операции
            FileOperationDto operation = fileImportService.getImportStatus(operationId);
            model.addAttribute("operation", operation);

            // Получаем клиента
            Client client = clientService.findClientEntityById(operation.getClientId())
                    .orElseThrow(() -> new FileOperationException("Клиент не найден"));
            model.addAttribute("client", client);

            return "import/status";
        } catch (Exception e) {
            log.error("Error getting import status: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при получении статуса импорта: " + e.getMessage());
            return "import/index";
        }
    }

    /**
     * REST эндпоинт для получения статуса импорта.
     */
    @GetMapping("/api/status/{operationId}")
    @ResponseBody
    public ResponseEntity<FileOperationDto> getImportStatus(@PathVariable Long operationId) {
        log.debug("GET API request to get import status for operation: {}", operationId);

        try {
            FileOperationDto operation = fileImportService.getImportStatus(operationId);
            return ResponseEntity.ok(operation);
        } catch (Exception e) {
            log.error("Error getting import status via API: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * REST эндпоинт для отмены импорта.
     */
    @PostMapping("/api/cancel/{operationId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelImport(@PathVariable Long operationId) {
        log.debug("POST API request to cancel import for operation: {}", operationId);

        try {
            boolean cancelled = fileImportService.cancelImport(operationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", cancelled);
            response.put("message", cancelled ? "Импорт успешно отменен" : "Не удалось отменить импорт");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error cancelling import: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ошибка при отмене импорта: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * REST эндпоинт для получения доступных маппингов полей.
     */
    @GetMapping("/api/mappings/{clientId}/{entityType}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAvailableMappings(
            @PathVariable Long clientId,
            @PathVariable String entityType) {

        log.debug("GET API request to get available mappings for client: {}, entity type: {}", clientId, entityType);

        try {
            List<Map<String, Object>> mappings = fieldMappingService.getAvailableMappingsForClient(clientId, entityType);
            return ResponseEntity.ok(mappings);
        } catch (Exception e) {
            log.error("Error getting available mappings: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * REST эндпоинт для получения метаданных полей сущности.
     */
    @GetMapping("/api/entity-fields/{entityType}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEntityFieldsMetadata(@PathVariable String entityType) {
        log.debug("GET API request to get entity fields metadata for entity type: {}", entityType);

        try {
            Map<String, Object> metadata = fieldMappingService.getEntityFieldsMetadata(entityType);
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            log.error("Error getting entity fields metadata: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Вспомогательный метод для форматирования размера файла.
     */
    private String formatFileSize(long size) {
        final long KB = 1024;
        final long MB = KB * 1024;
        final long GB = MB * 1024;

        if (size < KB) {
            return size + " bytes";
        } else if (size < MB) {
            return String.format("%.2f KB", (double) size / KB);
        } else if (size < GB) {
            return String.format("%.2f MB", (double) size / MB);
        } else {
            return String.format("%.2f GB", (double) size / GB);
        }
    }
}