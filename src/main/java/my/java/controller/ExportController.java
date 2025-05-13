// src/main/java/my/java/controller/ExportController.java
package my.java.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.ExportTemplate;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import my.java.service.client.ClientService;
import my.java.service.file.exporter.ExportTemplateService;
import my.java.service.file.exporter.FileExportService;
import my.java.service.file.metadata.EntityMetadata;
import my.java.service.file.metadata.EntityRegistry;
import my.java.service.file.options.FileWritingOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clients/{clientId}/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private final ClientService clientService;
    private final FileOperationRepository fileOperationRepository;
    private final FileExportService fileExportService;
    private final EntityRegistry entityRegistry;
    private final ExportTemplateService templateService;
    private final ObjectMapper objectMapper;

    /**
     * Отображение страницы экспорта
     */
    @GetMapping
    public String showExportForm(
            @PathVariable Long clientId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long templateId,
            // Добавляем параметр для предвыбора операции импорта
            @RequestParam(required = false) Long preselectedOperation,
            Model model,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("currentUri", request.getRequestURI());

                    // Загружаем список сущностей для экспорта
                    model.addAttribute("entityTypes", entityRegistry.getMainEntities());

                    // Загружаем доступные шаблоны для клиента
                    List<ExportTemplate> allTemplates = templateService.getAllTemplatesForClient(clientId);
                    model.addAttribute("allTemplates", allTemplates);

                    // Если указан тип сущности, загружаем поля
                    if (entityType != null && !entityType.isEmpty()) {
                        var entityMetadata = entityRegistry.getEntityMetadata(entityType);
                        if (entityMetadata != null) {
                            model.addAttribute("selectedEntityType", entityType);

                            // Загружаем шаблоны для данного типа сущности
                            List<ExportTemplate> entityTemplates = templateService.getTemplatesForEntityType(clientId, entityType);
                            model.addAttribute("templates", entityTemplates);

                            // Если указан ID шаблона, загружаем его
                            ExportTemplate selectedTemplate = null;
                            if (templateId != null) {
                                selectedTemplate = templateService.getTemplateById(templateId).orElse(null);
                            } else {
                                // Получаем шаблон по умолчанию, если есть
                                selectedTemplate = templateService.getDefaultTemplate(clientId, entityType).orElse(null);
                            }
                            model.addAttribute("selectedTemplate", selectedTemplate);

                            // Подготавливаем данные для шаблона
                            prepareTemplateData(model, selectedTemplate);

                            // Загружаем поля основной сущности
                            model.addAttribute("entityFields", entityMetadata.getPrefixedFields());

                            // Загружаем поля связанных сущностей и подготавливаем данные
                            prepareRelatedEntitiesData(model, entityType, selectedTemplate);

                            // Загружаем доступные стратегии экспорта
                            model.addAttribute("strategies", fileExportService.getAvailableStrategies());
                        }
                    }
                    // Если указана операция для предварительного выбора
                    if (preselectedOperation != null) {
                        model.addAttribute("preselectedOperation", preselectedOperation);
                    }

                    // Загружаем список операций импорта для клиента
                    List<FileOperation> importOperations = fileOperationRepository.findByClientIdAndOperationTypeAndStatus(
                            clientId,
                            FileOperation.OperationType.IMPORT,
                            FileOperation.OperationStatus.COMPLETED);
                    model.addAttribute("importOperations", importOperations);

                    return "export/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Подготавливает данные шаблона для использования в представлении
     */
    private void prepareTemplateData(Model model, ExportTemplate template) {
        if (template != null) {
            // Множество выбранных полей
            Set<String> selectedFields = template.getFields().stream()
                    .map(ExportTemplate.ExportField::getOriginalField)
                    .collect(Collectors.toSet());
            model.addAttribute("selectedFields", selectedFields);

            // Карта соответствия полей и их заголовков
            Map<String, String> fieldHeaders = template.getFields().stream()
                    .collect(Collectors.toMap(
                            ExportTemplate.ExportField::getOriginalField,
                            ExportTemplate.ExportField::getDisplayName,
                            (v1, v2) -> v1 // В случае дублей берем первое значение
                    ));
            model.addAttribute("fieldHeaders", fieldHeaders);

            // Параметры экспорта из шаблона
            FileWritingOptions options = template.getExportOptions();
            if (options != null) {
                // Основные параметры
                model.addAttribute("fileFormat", options.getFileType());
                model.addAttribute("includeHeader", options.isIncludeHeader());

                // Параметры CSV
                if (options.getDelimiter() != null) {
                    model.addAttribute("delimiter", options.getDelimiter().toString());
                }

                if (options.getQuoteChar() != null) {
                    model.addAttribute("quoteChar", options.getQuoteChar().toString());
                }

                if (options.getCharset() != null) {
                    model.addAttribute("encoding", options.getCharset().name());
                }

                // Параметры Excel
                if (options.getSheetName() != null) {
                    model.addAttribute("sheetName", options.getSheetName());
                }

                model.addAttribute("autoSizeColumns", options.isAutoSizeColumns());

                // Стратегия
                if (options.getAdditionalParams().containsKey("strategyId")) {
                    model.addAttribute("strategyId", options.getAdditionalParams().get("strategyId"));
                } else if (template.getStrategyId() != null) {
                    model.addAttribute("strategyId", template.getStrategyId());
                }
            } else {
                model.addAttribute("fileFormat", "csv");
                model.addAttribute("strategyId", template.getStrategyId());
            }
        }
    }

    /**
     * Подготавливает данные связанных сущностей для использования в представлении
     */
    private void prepareRelatedEntitiesData(Model model, String entityType, ExportTemplate template) {
        List<EntityMetadata> relatedEntities = entityRegistry.getRelatedEntities(entityType);
        if (!relatedEntities.isEmpty()) {
            Map<String, Map<String, EntityMetadata.FieldMetadata>> relatedFields = new HashMap<>();

            // Карта выбранных полей для каждой связанной сущности
            Map<String, Set<String>> relatedSelectedFields = new HashMap<>();

            for (EntityMetadata relatedEntity : relatedEntities) {
                String entityKey = relatedEntity.getEntityType();
                relatedFields.put(entityKey, relatedEntity.getPrefixedFields());

                // Если есть выбранный шаблон, подготавливаем выбранные поля
                if (template != null) {
                    Set<String> fields = template.getFields().stream()
                            .map(ExportTemplate.ExportField::getOriginalField)
                            .filter(field -> field.startsWith(entityKey + "."))
                            .collect(Collectors.toSet());
                    relatedSelectedFields.put(entityKey, fields);
                } else {
                    relatedSelectedFields.put(entityKey, Collections.emptySet());
                }
            }

            model.addAttribute("relatedFields", relatedFields);
            model.addAttribute("relatedSelectedFields", relatedSelectedFields);
        }
    }

    /**
     * Обработка запроса на экспорт данных
     */
    @PostMapping
    public String exportData(
            @PathVariable Long clientId,
            @RequestParam("entityType") String entityType,
            @RequestParam("format") String format,
            @RequestParam(value = "fields", required = false) List<String> fields,
            @RequestParam(value = "fieldsOrder", required = false) String fieldsOrder,
            @RequestParam(value = "strategyId", required = false) String strategyId,
            @RequestParam(value = "includeHeader", required = false, defaultValue = "true") Boolean includeHeader,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        log.info("POST запрос на экспорт данных для клиента: {}, тип сущности: {}, формат: {}", clientId, entityType, format);
        log.info("Порядок полей: {}", fieldsOrder);

        if (fields == null || fields.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите поля для экспорта");
            return "redirect:/clients/" + clientId + "/export?entityType=" + entityType;
        }

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Создаем настройки экспорта
            FileWritingOptions options = createOptionsFromParams(allParams);
            options.setFileType(format);
            options.setIncludeHeader(includeHeader);

            // Устанавливаем кодировку для CSV экспорта
            if (allParams.containsKey("encoding") && !allParams.get("encoding").isEmpty() && format.equalsIgnoreCase("csv")) {
                try {
                    options.setCharset(Charset.forName(allParams.get("encoding")));
                    log.debug("Установлена кодировка {} для экспорта", allParams.get("encoding"));
                } catch (Exception e) {
                    log.warn("Не удалось установить кодировку {}, используется UTF-8", allParams.get("encoding"));
                    options.setCharset(StandardCharsets.UTF_8);
                }
            }

            // Устанавливаем стратегию экспорта
            if (strategyId != null && !strategyId.isEmpty()) {
                options.getAdditionalParams().put("strategyId", strategyId);

                // Собираем параметры для стратегии из запроса
                for (Map.Entry<String, String> entry : allParams.entrySet()) {
                    if (entry.getKey().startsWith("strategy_")) {
                        options.getAdditionalParams().put(entry.getKey(), entry.getValue());
                    }
                }
            }

            // Сохраняем порядок полей, если он определен
            if (fieldsOrder != null && !fieldsOrder.isEmpty()) {
                options.getAdditionalParams().put("fieldsOrder", fieldsOrder);
                log.debug("Сохранен порядок полей: {}", fieldsOrder);
            }

            // Если указан ID шаблона в параметрах, используем порядок полей из шаблона
            if (allParams.containsKey("templateId")) {
                try {
                    Long templateId = Long.parseLong(allParams.get("templateId"));
                    Optional<ExportTemplate> template = templateService.getTemplateById(templateId);

                    if (template.isPresent() && !template.get().getFields().isEmpty()) {
                        ExportTemplate existingTemplate = template.get();

                        // Получаем поля в порядке, сохраненном в БД
                        List<String> orderedFields = existingTemplate.getFields().stream()
                                .map(ExportTemplate.ExportField::getOriginalField)
                                .collect(Collectors.toList());

                        log.info("Порядок полей из шаблона: {}", orderedFields);

                        // Устанавливаем порядок в параметры, переопределяя fieldsOrder из запроса
                        options.getAdditionalParams().put("fieldsOrder", objectMapper.writeValueAsString(orderedFields));
                        log.info("Обновлен порядок полей из шаблона в БД");
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при получении порядка полей из шаблона: {}", e.getMessage());
                }
            }

            // Собираем все заголовки полей из формы
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("header_")) {
                    options.getAdditionalParams().put(entry.getKey(), entry.getValue());
                }
            }

            // Собираем параметры фильтрации
            Map<String, Object> filterParams = extractFilterParams(allParams);

            // Запускаем асинхронный экспорт
            CompletableFuture<FileOperationDto> future = fileExportService.exportDataAsync(
                    client, entityType, fields, filterParams, options);

            // Получаем результат операции
            FileOperationDto operation = future.join();

            // Добавляем сообщение об успешном начале экспорта
            redirectAttributes.addFlashAttribute("successMessage",
                    "Экспорт данных успешно начат. ID операции: " + operation.getId());

            // Перенаправляем на страницу операций
            return "redirect:/clients/" + clientId + "/operations/" + operation.getId();

        } catch (IllegalArgumentException e) {
            log.error("Ошибка при экспорте: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients/" + clientId + "/export?entityType=" + entityType;
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при экспорте: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Произошла ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export?entityType=" + entityType;
        }
    }

    /**
     * Обработка запроса на прямой экспорт данных (с диалогом сохранения)
     */
    @PostMapping("/direct-download")
    public ResponseEntity<byte[]> downloadDirectly(
            @PathVariable Long clientId,
            @RequestParam("entityType") String entityType,
            @RequestParam("format") String format,
            @RequestParam(value = "fields", required = false) List<String> fields,
            @RequestParam(value = "fieldsOrder", required = false) String fieldsOrder,
            @RequestParam(value = "strategyId", required = false) String strategyId,
            @RequestParam(value = "includeHeader", required = false, defaultValue = "true") Boolean includeHeader,
            @RequestParam Map<String, String> allParams) {

        log.info("POST запрос на прямое скачивание данных для клиента: {}, тип сущности: {}, формат: {}",
                clientId, entityType, format);
        log.info("Порядок полей: {}", fieldsOrder);

        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Пожалуйста, выберите поля для экспорта");
        }

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Создаем настройки экспорта из параметров формы
            FileWritingOptions options = createOptionsFromParams(allParams);
            options.setFileType(format);
            options.setIncludeHeader(includeHeader);

            // Устанавливаем стратегию экспорта, если указана
            if (strategyId != null && !strategyId.isEmpty()) {
                options.getAdditionalParams().put("strategyId", strategyId);
            }

            // Сохраняем порядок полей, если он определен
            if (fieldsOrder != null && !fieldsOrder.isEmpty()) {
                options.getAdditionalParams().put("fieldsOrder", fieldsOrder);
                log.debug("Сохранен порядок полей: {}", fieldsOrder);
            }

            // Если указан ID шаблона в параметрах, используем порядок полей из шаблона
            if (allParams.containsKey("templateId")) {
                try {
                    Long templateId = Long.parseLong(allParams.get("templateId"));
                    Optional<ExportTemplate> template = templateService.getTemplateById(templateId);

                    if (template.isPresent() && !template.get().getFields().isEmpty()) {
                        ExportTemplate existingTemplate = template.get();

                        // Получаем поля в порядке, сохраненном в БД
                        List<String> orderedFields = existingTemplate.getFields().stream()
                                .map(ExportTemplate.ExportField::getOriginalField)
                                .collect(Collectors.toList());

                        log.info("Порядок полей из шаблона: {}", orderedFields);

                        // Устанавливаем порядок в параметры, переопределяя fieldsOrder из запроса
                        options.getAdditionalParams().put("fieldsOrder", objectMapper.writeValueAsString(orderedFields));
                        log.info("Обновлен порядок полей из шаблона в БД");
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при получении порядка полей из шаблона: {}", e.getMessage());
                }
            }

            // Собираем все заголовки полей из формы
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("header_")) {
                    options.getAdditionalParams().put(entry.getKey(), entry.getValue());
                }
            }

            // Извлекаем параметры фильтрации
            Map<String, Object> filterParams = extractFilterParams(allParams);

            // Создаем временную операцию для отслеживания
            FileOperation tempOperation = new FileOperation();
            tempOperation.setClient(client);
            tempOperation.setOperationType(FileOperation.OperationType.EXPORT);
            tempOperation.setFileType(format);
            tempOperation.setStatus(FileOperation.OperationStatus.PROCESSING);

            // Выполняем экспорт во временный файл
            Path tempFile = fileExportService.exportDirectly(
                    client, entityType, fields, filterParams, options, tempOperation);

            // Проверяем, что файл создан
            if (tempFile == null || !Files.exists(tempFile)) {
                throw new FileOperationException("Не удалось создать файл экспорта");
            }

            // Читаем содержимое файла
            byte[] fileContent = Files.readAllBytes(tempFile);

            // Генерируем имя файла с именем клиента и датой/временем
            String fileName = generateFileName(client, entityType, format);

            // Определяем MIME-тип
            MediaType mediaType = getMediaType(format);

            // Устанавливаем заголовки для скачивания файла
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            headers.setContentType(mediaType);

            // Удаляем временный файл
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception e) {
                log.warn("Не удалось удалить временный файл: {}", e.getMessage());
            }

            // Возвращаем напрямую массив байтов вместо ByteArrayResource
            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Ошибка при скачивании данных: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при скачивании данных: " + e.getMessage(), e);
        }
    }

    /**
     * Создает объект FileWritingOptions из параметров запроса
     */
    private FileWritingOptions createOptionsFromParams(Map<String, String> params) {
        FileWritingOptions options = new FileWritingOptions();

        // Устанавливаем тип файла
        if (params.containsKey("format")) {
            options.setFileType(params.get("format"));
        }

        // Общие настройки
        if (params.containsKey("includeHeader")) {
            options.setIncludeHeader(Boolean.parseBoolean(params.get("includeHeader")));
        }

        // Настройки для CSV
        if (params.containsKey("delimiter") && !params.get("delimiter").isEmpty()) {
            options.setDelimiter(params.get("delimiter").charAt(0));
        }

        if (params.containsKey("quoteChar") && !params.get("quoteChar").isEmpty()) {
            options.setQuoteChar(params.get("quoteChar").charAt(0));
        }

        if (params.containsKey("encoding") && !params.get("encoding").isEmpty()) {
            try {
                options.setCharset(Charset.forName(params.get("encoding")));
            } catch (Exception e) {
                log.warn("Неверная кодировка: {}, используется UTF-8", params.get("encoding"));
            }
        }

        // Настройки для Excel
        if (params.containsKey("sheetName")) {
            options.setSheetName(params.get("sheetName"));
        }

        if (params.containsKey("autoSizeColumns")) {
            options.setAutoSizeColumns(Boolean.parseBoolean(params.get("autoSizeColumns")));
        }

        // Дополнительные параметры (включая параметры стратегии)
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if ((entry.getKey().startsWith("strategy_") || entry.getKey().startsWith("header_")) &&
                    !entry.getValue().isEmpty()) {
                options.getAdditionalParams().put(entry.getKey(), entry.getValue());
            }
        }

        return options;
    }

    /**
     * Генерирует имя файла в формате "Имя клиента_год_дата_время"
     */
    private String generateFileName(Client client, String entityType, String format) {
        // Заменяем недопустимые символы в имени клиента
        String clientName = client.getName().replaceAll("[^a-zA-Zа-яА-Я0-9_]", "_");
        // Форматируем текущую дату и время
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss"));
        return clientName + "_" + dateTime + "." + format.toLowerCase();
    }

    /**
     * Скачивание экспортированного файла
     */
    @GetMapping("/download/{operationId}")
    public ResponseEntity<Resource> downloadExportedFile(
            @PathVariable Long clientId,
            @PathVariable Long operationId,
            HttpServletResponse response) {

        log.debug("GET запрос на скачивание экспортированного файла для операции: {}", operationId);

        try {
            // Проверяем существование клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Получаем операцию
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция с ID " + operationId + " не найдена"));

            // Проверяем, что операция принадлежит клиенту
            if (!operation.getClient().getId().equals(clientId)) {
                throw new IllegalArgumentException("Операция не принадлежит данному клиенту");
            }

            // Проверяем, что операция завершена
            if (operation.getStatus() != FileOperation.OperationStatus.COMPLETED) {
                throw new FileOperationException("Операция еще не завершена");
            }

            // Загружаем файл
            String filePath = operation.getResultFilePath();
            if (filePath == null || filePath.isEmpty()) {
                throw new FileOperationException("Путь к файлу не указан");
            }

            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new FileOperationException("Файл не найден");
            }

            // Читаем содержимое файла
            byte[] content = Files.readAllBytes(path);

            // Устанавливаем заголовки для скачивания файла
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + operation.getFileName() + "\"");

            // Определяем MIME-тип
            MediaType mediaType = getMediaType(operation.getFileType());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(mediaType)
                    .body(new ByteArrayResource(content));

        } catch (IllegalArgumentException | FileOperationException e) {
            log.error("Ошибка при скачивании файла: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("Ошибка при чтении файла: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Непредвиденная ошибка: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Извлечение параметров фильтрации
     */
    private Map<String, Object> extractFilterParams(Map<String, String> allParams) {
        Map<String, Object> filterParams = new HashMap<>();

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("filter_") && !entry.getValue().isEmpty()) {
                String paramName = entry.getKey().substring("filter_".length());
                filterParams.put(paramName, entry.getValue());
            }
        }

        return filterParams;
    }

    /**
     * Определяет MediaType по расширению файла
     */
    private MediaType getMediaType(String fileType) {
        if (fileType == null) return MediaType.APPLICATION_OCTET_STREAM;

        switch (fileType.toLowerCase()) {
            case "csv":
                // Используем octet-stream вместо text/csv для совместимости с ByteArrayResource
                return MediaType.APPLICATION_OCTET_STREAM;
            case "xlsx":
                return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls":
                return MediaType.parseMediaType("application/vnd.ms-excel");
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}