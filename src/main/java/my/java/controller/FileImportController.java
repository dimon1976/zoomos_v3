package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.ClientDto;
import my.java.dto.FileOperationDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.service.client.ClientService;
import my.java.service.file.importer.FileImportService;
import my.java.service.file.mapping.FieldMappingService;
import my.java.service.file.metadata.EntityFieldService;
import my.java.util.PathResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Контроллер для работы с импортом файлов
 */
@Controller
@RequestMapping("/import")
@RequiredArgsConstructor
@Slf4j
public class FileImportController {

    private final FileImportService fileImportService;
    private final ClientService clientService;
    private final FieldMappingService fieldMappingService;
    private final PathResolver pathResolver;
    private final EntityFieldService entityFieldService;

    // Карта активных операций импорта для отслеживания
    private final Map<Long, CompletableFuture<FileOperationDto>> activeImports =
            Collections.synchronizedMap(new HashMap<>());

    @GetMapping
    public String showImportPage(Model model) {
        log.debug("Запрос на отображение страницы импорта");
        return "import/index";
    }

    @GetMapping("/select-client")
    public String showSelectClientPage(Model model) {
        log.debug("Запрос на отображение страницы выбора клиента");
        model.addAttribute("clients", clientService.getAllClients());
        return "import/select-client";
    }

    @GetMapping("/{clientId}")
    public String showUploadPage(@PathVariable Long clientId, Model model) {
        log.debug("Запрос на отображение страницы загрузки файла для клиента: {}", clientId);

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

    @PostMapping("/{clientId}/analyze")
    public String analyzeFile(
            @PathVariable Long clientId,
            @RequestParam("file") MultipartFile file,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Запрос на анализ файла для клиента: {}, имя файла: {}", clientId, file.getOriginalFilename());

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите файл для загрузки");
            return "redirect:/import/" + clientId;
        }

        return clientService.getClientById(clientId)
                .map(client -> {
                    try {
                        return processFileAnalysis(clientId, file, model, client);
                    } catch (Exception e) {
                        log.error("Ошибка при анализе файла: {}", e.getMessage(), e);
                        redirectAttributes.addFlashAttribute("errorMessage",
                                "Ошибка при анализе файла: " + e.getMessage());
                        return "redirect:/import/" + clientId;
                    }
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return "redirect:/import/select-client";
                });
    }

    private String processFileAnalysis(Long clientId, MultipartFile file, Model model, ClientDto client)
            throws Exception {
        // Сохраняем файл во временную директорию
        Path tempFilePath = pathResolver.saveToTempFile(file, "analyze");
        log.debug("Файл сохранен во временную директорию: {}", tempFilePath);

        // Анализируем файл
        Map<String, Object> analysis = fileImportService.analyzeFile(file);

        // Подробный лог образца данных
        if (analysis.containsKey("sampleData")) {
            List<?> sampleData = (List<?>) analysis.get("sampleData");
            log.debug("Размер образца данных: {}", sampleData.size());
            if (sampleData.isEmpty()) {
                log.warn("Образец данных пуст!");
            } else {
                log.debug("Первая строка образца: {}", sampleData.get(0));
            }
        } else {
            log.warn("Образец данных отсутствует в результате анализа!");
        }

        // Проверяем наличие заголовков
        if (analysis.containsKey("headers")) {
            log.debug("Заголовки: {}", analysis.get("headers"));
        } else {
            log.warn("Заголовки отсутствуют в результате анализа!");
        }

        // Добавляем данные в модель
        prepareAnalysisModel(model, client, file, tempFilePath, analysis, clientId);

        // Отладка модели
        log.debug("Модель перед отправкой на страницу: {}", model.asMap().keySet());

        // Возвращаем страницу настройки импорта
        return "import/configure";
    }

    private void prepareAnalysisModel(Model model, ClientDto client, MultipartFile file,
                                      Path tempFilePath, Map<String, Object> analysis, Long clientId) {
        model.addAttribute("client", client);
        model.addAttribute("analysis", analysis);
        model.addAttribute("fileName", file.getOriginalFilename());
        model.addAttribute("fileSize", formatFileSize(file.getSize()));
        model.addAttribute("tempFilePath", tempFilePath.toString());

        // Получаем доступные маппинги полей
        String entityType = getEntityTypeFromAnalysis(analysis);
        List<Map<String, Object>> mappings = fieldMappingService.getAvailableMappingsForClient(clientId, entityType);
        model.addAttribute("availableMappings", mappings);

        // Добавляем метаданные о полях всех типов сущностей
        model.addAttribute("entityFieldsMetadata", entityFieldService.getAllEntityFieldsMetadata());
        model.addAttribute("supportedEntityTypes", entityFieldService.getSupportedEntityTypes());
    }

    private String getEntityTypeFromAnalysis(Map<String, Object> analysis) {
        return analysis.containsKey("entityType") ? (String) analysis.get("entityType") : "product_with_related";
    }


    @PostMapping("/{clientId}/import")
    public String importFile(
            @PathVariable Long clientId,
            @RequestParam(value = "tempFilePath", required = false) String tempFilePath,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "mappingId", required = false) String mappingIdStr,
            @RequestParam(value = "strategyId", required = false) Long strategyId,
            @RequestParam(value = "entityType", required = false, defaultValue = "product") String entityType,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes) {

        log.info("Запрос на импорт файла для клиента: {}, имя файла: {}, путь к файлу: {}",
                clientId, fileName, tempFilePath);

        if (tempFilePath == null || tempFilePath.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Отсутствует путь к файлу для импорта");
            return "redirect:/import/" + clientId;
        }

        try {
            Client client = getClientForImport(clientId, redirectAttributes);
            Path filePath = validateTempFile(tempFilePath, redirectAttributes, clientId);
            Long mappingId = parseMappingId(mappingIdStr, params);

            Map<String, String> importParams = prepareImportParams(entityType, params);

            // Обработка создания нового маппинга, если необходимо
            if (mappingIdStr != null && mappingIdStr.equals("new")) {
                createNewMappingIfRequested(clientId, entityType, params, importParams);
            }

            return processImport(clientId, fileName, client, filePath, mappingId,
                    strategyId, importParams, redirectAttributes);

        } catch (FileOperationException e) {
            // Эта ошибка уже обработана и добавлена в redirectAttributes
            return "redirect:/import/" + clientId;
        } catch (Exception e) {
            log.error("Ошибка при импорте файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при импорте файла: " + e.getMessage());
            return "redirect:/import/" + clientId;
        }
    }

    private Client getClientForImport(Long clientId, RedirectAttributes redirectAttributes) {
        return clientService.findClientEntityById(clientId)
                .orElseThrow(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент с ID " + clientId + " не найден");
                    return new FileOperationException("Клиент с ID " + clientId + " не найден");
                });
    }

    private Path validateTempFile(String tempFilePath, RedirectAttributes redirectAttributes, Long clientId) {
        Path filePath = Paths.get(tempFilePath);
        if (!Files.exists(filePath)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Временный файл не найден. Возможно, сессия истекла.");
            throw new FileOperationException("Временный файл не найден");
        }
        return filePath;
    }

    private Long parseMappingId(String mappingIdStr, Map<String, String> params) {
        if (mappingIdStr == null || mappingIdStr.equals("new")) {
            return null;
        }

        try {
            return Long.parseLong(mappingIdStr);
        } catch (NumberFormatException e) {
            log.warn("Невозможно преобразовать mappingId к Long: {}", mappingIdStr);
            return null;
        }
    }

    private Map<String, String> prepareImportParams(String entityType, Map<String, String> params) {
        Map<String, String> importParams = new HashMap<>();
        importParams.put("entityType", entityType);

        // Добавляем дополнительные параметры
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!isReservedParam(entry.getKey())) {
                importParams.put(entry.getKey(), entry.getValue());
                log.debug("Добавлен параметр импорта: {} = {}", entry.getKey(), entry.getValue());
            }
        }

        // Явно добавляем параметры стратегии обработки
        if (params.containsKey("processingStrategy")) {
            importParams.put("processingStrategy", params.get("processingStrategy"));
            log.debug("Установлена стратегия обработки: {}", params.get("processingStrategy"));
        }

        // Явно добавляем параметры пакетной обработки
        if (params.containsKey("batchSize")) {
            importParams.put("batchSize", params.get("batchSize"));
            log.debug("Установлен размер пакета: {}", params.get("batchSize"));
        }

        // Явно добавляем параметры обработки ошибок
        if (params.containsKey("errorHandling")) {
            importParams.put("errorHandling", params.get("errorHandling"));
            log.debug("Установлен способ обработки ошибок: {}", params.get("errorHandling"));
        }

        // Явно добавляем параметры обработки дубликатов
        if (params.containsKey("duplicateHandling")) {
            importParams.put("duplicateHandling", params.get("duplicateHandling"));
            log.debug("Установлен способ обработки дубликатов: {}", params.get("duplicateHandling"));
        }

        // Явно добавляем флаги
        if (params.containsKey("validateData")) {
            importParams.put("validateData", "true");
            log.debug("Включена валидация данных");
        }

        if (params.containsKey("trimWhitespace")) {
            importParams.put("trimWhitespace", "true");
            log.debug("Включена обрезка пробелов");
        }

        if (params.containsKey("generateReport")) {
            importParams.put("generateReport", "true");
            log.debug("Включена генерация отчета");
        }

        if (params.containsKey("notifyOnComplete")) {
            importParams.put("notifyOnComplete", "true");
            log.debug("Включено уведомление о завершении");
        }

        return importParams;
    }

    private boolean isReservedParam(String paramName) {
        return paramName.equals("tempFilePath") || paramName.equals("fileName") ||
                paramName.equals("mappingId") || paramName.equals("strategyId") ||
                paramName.equals("entityType");
    }

    private void createNewMappingIfRequested(Long clientId, String entityType,
                                             Map<String, String> params, Map<String, String> importParams) {
        if (Boolean.parseBoolean(params.getOrDefault("saveMapping", "false"))) {
            String mappingName = params.get("mappingName");
            String mappingDescription = params.get("mappingDescription");

            if (mappingName != null && !mappingName.trim().isEmpty()) {
                // Получаем все маппинги полей из запроса
                Map<String, String> fieldMapping = extractFieldMappingFromParams(params);

                // Создаем новый маппинг
                Long mappingId = fieldMappingService.createMapping(
                        mappingName,
                        mappingDescription,
                        clientId,
                        entityType,
                        fieldMapping
                );

                log.info("Создан новый маппинг полей: {}, ID: {}", mappingName, mappingId);
                importParams.put("createdMappingId", mappingId.toString());
            }
        }
    }

    private String processImport(Long clientId, String fileName, Client client, Path filePath,
                                 Long mappingId, Long strategyId, Map<String, String> importParams,
                                 RedirectAttributes redirectAttributes) {
        // Импортируем файл напрямую, используя его путь
        FileOperationDto operation = fileImportService.processUploadedFile(
                filePath, client, mappingId, strategyId, importParams);

        if (operation != null) {
            // Добавляем сообщение об успешном начале импорта
            redirectAttributes.addFlashAttribute("successMessage",
                    "Импорт файла '" + fileName + "' успешно запущен");

            // Перенаправляем на страницу прогресса импорта вместо страницы клиента
            return "redirect:/import/progress/" + operation.getId();
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Не удалось запустить импорт файла");
            return "redirect:/import/" + clientId;
        }
    }

    @GetMapping("/status/{operationId}")
    public String showImportStatus(@PathVariable Long operationId, Model model) {
        log.debug("Запрос на отображение статуса импорта для операции: {}", operationId);

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
            log.error("Ошибка при получении статуса импорта: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при получении статуса импорта: " + e.getMessage());
            return "import/index";
        }
    }

    @GetMapping("/api/status/{operationId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getImportStatus(@PathVariable Long operationId) {
        log.debug("API запрос на получение статуса импорта для операции: {}", operationId);

        try {
            FileOperationDto operation = fileImportService.getImportStatus(operationId);
            return ResponseEntity.ok(createImportStatusResponse(operation));
        } catch (Exception e) {
            log.error("Ошибка при получении статуса импорта через API: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorResponse(e.getMessage()));
        }
    }

    private Map<String, Object> createImportStatusResponse(FileOperationDto operation) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", operation.getId());
        response.put("status", operation.getStatus().toString());
        response.put("processingProgress", operation.getProcessingProgress() != null ?
                operation.getProcessingProgress() : 0);
        response.put("processedRecords", operation.getProcessedRecords() != null ?
                operation.getProcessedRecords() : 0);
        response.put("totalRecords", operation.getTotalRecords() != null ?
                operation.getTotalRecords() : 0);
        response.put("recordCount", operation.getRecordCount() != null ?
                operation.getRecordCount() : 0);
        response.put("errorMessage", operation.getErrorMessage());
        response.put("startedAt", operation.getStartedAt());
        response.put("completedAt", operation.getCompletedAt());
        response.put("duration", operation.getDuration());

        // Расчет оставшегося времени (если операция в процессе)
        addRemainingTimeEstimation(response, operation);

        return response;
    }

    private void addRemainingTimeEstimation(Map<String, Object> response, FileOperationDto operation) {
        boolean canEstimateTime = isProcessingAndHasProgress(operation);

        if (canEstimateTime) {
            try {
                long elapsedTime = System.currentTimeMillis() -
                        operation.getStartedAt().toInstant().toEpochMilli();
                double recordsPerMs = operation.getProcessedRecords() / (double) elapsedTime;
                long remainingRecords = operation.getTotalRecords() - operation.getProcessedRecords();
                long estimatedTimeRemaining = (long) (remainingRecords / recordsPerMs);

                response.put("estimatedTimeRemaining", estimatedTimeRemaining);
            } catch (Exception e) {
                log.warn("Ошибка при расчете оставшегося времени: {}", e.getMessage());
                // Не добавляем оценку времени в случае ошибки
            }
        }
    }

    private boolean isProcessingAndHasProgress(FileOperationDto operation) {
        return operation.getStatus() == FileOperation.OperationStatus.PROCESSING &&
                operation.getProcessingProgress() != null &&
                operation.getProcessingProgress() > 0 &&
                operation.getStartedAt() != null &&
                operation.getProcessedRecords() != null &&
                operation.getProcessedRecords() > 0 &&
                operation.getTotalRecords() != null &&
                operation.getTotalRecords() > 0;
    }

    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", errorMessage);
        return errorResponse;
    }

    @PostMapping("/api/cancel/{operationId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelImport(@PathVariable Long operationId) {
        log.debug("API запрос на отмену импорта для операции: {}", operationId);

        try {
            boolean cancelled = fileImportService.cancelImport(operationId);
            Map<String, Object> response = createCancelResponse(cancelled);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при отмене импорта: {}", e.getMessage(), e);
            Map<String, Object> response = createErrorResponse("Ошибка при отмене импорта: " + e.getMessage());
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }
    }

    private Map<String, Object> createCancelResponse(boolean cancelled) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", cancelled);
        response.put("message", cancelled ? "Импорт успешно отменен" : "Не удалось отменить импорт");
        return response;
    }

    @GetMapping("/api/mappings/{clientId}/{entityType}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAvailableMappings(
            @PathVariable Long clientId,
            @PathVariable String entityType) {

        log.debug("API запрос на получение доступных маппингов для клиента: {}, тип сущности: {}",
                clientId, entityType);

        try {
            List<Map<String, Object>> mappings =
                    fieldMappingService.getAvailableMappingsForClient(clientId, entityType);
            return ResponseEntity.ok(mappings);
        } catch (Exception e) {
            log.error("Ошибка при получении доступных маппингов: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Получает детали маппинга по ID
     */
    @GetMapping("/api/mapping-details/{mappingId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getMappingDetails(@PathVariable Long mappingId) {
        log.debug("Запрос на получение деталей маппинга по ID: {}", mappingId);
        Map<String, String> mappingDetails = fieldMappingService.getMappingById(mappingId);

        if (mappingDetails.isEmpty()) {
            log.warn("Детали маппинга с ID {} не найдены", mappingId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(mappingDetails);
    }

    @GetMapping("/api/entity-fields/{entityType}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEntityFieldsMetadata(@PathVariable String entityType) {
        log.debug("API запрос на получение метаданных полей сущности типа: {}", entityType);

        try {
            Map<String, Object> metadata = fieldMappingService.getEntityFieldsMetadata(entityType);
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            log.error("Ошибка при получении метаданных полей сущности: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

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

    private Map<String, String> extractFieldMappingFromParams(Map<String, String> params) {
        Map<String, String> fieldMapping = new HashMap<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("mapping[") && key.endsWith("]")) {
                // Извлекаем имя исходного поля из ключа (между "mapping[" и "]")
                String sourceField = key.substring(8, key.length() - 1);
                String targetField = entry.getValue();

                // Добавляем в маппинг только если целевое поле не пустое
                if (targetField != null && !targetField.trim().isEmpty()) {
                    fieldMapping.put(sourceField, targetField);
                }
            }
        }

        return fieldMapping;
    }
}