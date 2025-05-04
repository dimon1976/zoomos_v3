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

    /**
     * Отображение страницы экспорта
     */
    @GetMapping
    public String showExportForm(
            @PathVariable Long clientId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long templateId,
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

            // Параметры формата файла
            model.addAttribute("fileFormat", template.getFileType());
            model.addAttribute("strategyId", template.getStrategyId());
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
            @RequestParam(value = "saveAsTemplate", required = false) Boolean saveAsTemplate,
            @RequestParam(value = "templateName", required = false) String templateName,
            @RequestParam(value = "strategyId", required = false) String strategyId,
            @RequestParam(value = "encoding", required = false) String encoding,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        log.info("POST запрос на экспорт данных для клиента: {}, тип сущности: {}, формат: {}", clientId, entityType, format);

        if (fields == null || fields.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите поля для экспорта");
            return "redirect:/clients/" + clientId + "/export?entityType=" + entityType;
        }

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Создаем настройки экспорта
            FileWritingOptions options = FileWritingOptions.fromMap(allParams);
            options.setFileType(format);

            // Устанавливаем кодировку для CSV экспорта
            if (encoding != null && !encoding.isEmpty() && format.equalsIgnoreCase("csv")) {
                try {
                    options.setCharset(Charset.forName(encoding));
                    log.debug("Установлена кодировка {} для экспорта", encoding);
                } catch (Exception e) {
                    log.warn("Не удалось установить кодировку {}, используется UTF-8", encoding);
                    options.setCharset(StandardCharsets.UTF_8);
                }
            }

            // Устанавливаем стратегию экспорта
            Map<String, String> strategyParams = new HashMap<>();
            if (strategyId != null && !strategyId.isEmpty()) {
                options.getAdditionalParams().put("strategyId", strategyId);

                // Собираем параметры для стратегии из запроса
                for (Map.Entry<String, String> entry : allParams.entrySet()) {
                    if (entry.getKey().startsWith("strategy_")) {
                        String paramName = entry.getKey().substring("strategy_".length());
                        strategyParams.put(paramName, entry.getValue());
                    }
                }
            }

            // Собираем все заголовки полей из формы
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("header_")) {
                    options.getAdditionalParams().put(entry.getKey(), entry.getValue());
                }
            }

            // Применяем порядок полей, если он указан
            if (fieldsOrder != null && !fieldsOrder.isEmpty()) {
                options.getAdditionalParams().put("fieldsOrder", fieldsOrder);

                // Если у нас есть порядок полей в JSON, преобразуем его в список и используем для сортировки
                try {
                    List<String> orderedFields = new ArrayList<>();
                    // Удаляем квадратные скобки и кавычки из строки JSON, затем разбиваем по запятой
                    String cleanJson = fieldsOrder.replaceAll("[\\[\\]\"]", "");
                    String[] fieldArray = cleanJson.split(",");

                    orderedFields.addAll(Arrays.asList(fieldArray));

                    // Если порядок полей действительно задан, используем его вместо исходного списка
                    if (!orderedFields.isEmpty()) {
                        // Фильтруем, оставляя только те поля, которые есть в исходном списке
                        List<String> filteredOrderedFields = orderedFields.stream()
                                .filter(fields::contains)
                                .collect(Collectors.toList());

                        // Добавляем поля, которые выбраны, но не указаны в порядке
                        for (String field : fields) {
                            if (!filteredOrderedFields.contains(field)) {
                                filteredOrderedFields.add(field);
                            }
                        }

                        // Заменяем исходный список на отсортированный
                        fields = filteredOrderedFields;
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при разборе порядка полей: {}", e.getMessage());
                }
            }

            // Извлекаем параметры фильтрации
            Map<String, Object> filterParams = extractFilterParams(allParams);

            // Запускаем асинхронный экспорт
            CompletableFuture<FileOperationDto> future = fileExportService.exportDataAsync(
                    client, entityType, fields, filterParams, options);

            // Получаем результат операции
            FileOperationDto operation = future.join();

            // Если требуется сохранить как шаблон
            if (Boolean.TRUE.equals(saveAsTemplate) && templateName != null && !templateName.isEmpty()) {
                saveAsTemplate(client, entityType, fields, format, strategyId, allParams, templateName);
            }

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
            @RequestParam(value = "encoding", required = false) String encoding,
            @RequestParam Map<String, String> allParams) {

        log.info("POST запрос на прямое скачивание данных для клиента: {}, тип сущности: {}, формат: {}",
                clientId, entityType, format);

        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Пожалуйста, выберите поля для экспорта");
        }

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Создаем настройки экспорта из параметров формы
            FileWritingOptions options = FileWritingOptions.fromMap(allParams);
            options.setFileType(format);

            // Устанавливаем кодировку для CSV экспорта
            if (encoding != null && !encoding.isEmpty() && format.equalsIgnoreCase("csv")) {
                try {
                    options.setCharset(Charset.forName(encoding));
                    log.debug("Установлена кодировка {} для экспорта", encoding);
                } catch (Exception e) {
                    log.warn("Не удалось установить кодировку {}, используется UTF-8", encoding);
                    options.setCharset(StandardCharsets.UTF_8);
                }
            }

            // Устанавливаем стратегию экспорта, если указана
            if (strategyId != null && !strategyId.isEmpty()) {
                options.getAdditionalParams().put("strategyId", strategyId);
            }

            // Собираем все заголовки полей из формы
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("header_")) {
                    options.getAdditionalParams().put(entry.getKey(), entry.getValue());
                }
            }

            // Применяем порядок полей, если он указан
            if (fieldsOrder != null && !fieldsOrder.isEmpty()) {
                options.getAdditionalParams().put("fieldsOrder", fieldsOrder);

                // Если у нас есть порядок полей в JSON, преобразуем его в список и используем для сортировки
                try {
                    List<String> orderedFields = new ArrayList<>();
                    // Удаляем квадратные скобки и кавычки из строки JSON, затем разбиваем по запятой
                    String cleanJson = fieldsOrder.replaceAll("[\\[\\]\"]", "");
                    String[] fieldArray = cleanJson.split(",");

                    orderedFields.addAll(Arrays.asList(fieldArray));

                    // Если порядок полей действительно задан, используем его вместо исходного списка
                    if (!orderedFields.isEmpty()) {
                        // Фильтруем, оставляя только те поля, которые есть в исходном списке
                        List<String> filteredOrderedFields = orderedFields.stream()
                                .filter(fields::contains)
                                .collect(Collectors.toList());

                        // Добавляем поля, которые выбраны, но не указаны в порядке
                        for (String field : fields) {
                            if (!filteredOrderedFields.contains(field)) {
                                filteredOrderedFields.add(field);
                            }
                        }

                        // Заменяем исходный список на отсортированный
                        fields = filteredOrderedFields;
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при разборе порядка полей: {}", e.getMessage());
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
     * Создание шаблона экспорта из текущих настроек
     */
    private void saveAsTemplate(Client client, String entityType, List<String> fields,
                                String format, String strategyId, Map<String, String> params,
                                String templateName) {
        try {
            ExportTemplate template = new ExportTemplate();
            template.setClient(client);
            template.setEntityType(entityType);
            template.setName(templateName);
            template.setFileType(format);
            template.setStrategyId(strategyId);

            // Маппинг полей с настройками заголовков
            for (String field : fields) {
                ExportTemplate.ExportField exportField = new ExportTemplate.ExportField();
                exportField.setOriginalField(field);

                // Проверяем, задан ли пользовательский заголовок
                String headerKey = "header_" + field.replace(".", "_");
                if (params.containsKey(headerKey)) {
                    exportField.setDisplayName(params.get(headerKey));
                } else {
                    exportField.setDisplayName(getDefaultDisplayName(field));
                }

                template.getFields().add(exportField);
            }

            // Сохраняем параметры файла
            Map<String, String> fileOptions = new HashMap<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey().startsWith("file_") ||
                        entry.getKey().equals("encoding") ||
                        entry.getKey().equals("delimiter") ||
                        entry.getKey().equals("quoteChar") ||
                        entry.getKey().equals("sheetName") ||
                        entry.getKey().equals("autoSizeColumns") ||
                        entry.getKey().equals("includeHeader")) {
                    fileOptions.put(entry.getKey(), entry.getValue());
                }
            }

            // Сохраняем порядок полей, если он указан
            if (params.containsKey("fieldsOrder")) {
                fileOptions.put("fieldsOrder", params.get("fieldsOrder"));
            }

            // Преобразуем параметры в JSON и сохраняем
            ObjectMapper objectMapper = new ObjectMapper();
            String fileOptionsJson = objectMapper.writeValueAsString(fileOptions);
            template.setFileOptions(fileOptionsJson);

            // Сохраняем шаблон
            templateService.saveTemplate(template);
            log.info("Создан новый шаблон экспорта: {}", template.getName());

        } catch (Exception e) {
            log.error("Ошибка при сохранении шаблона: {}", e.getMessage(), e);
            // Не прерываем основной процесс экспорта в случае ошибки сохранения шаблона
        }
    }

    /**
     * Преобразует имя поля в отображаемое название
     */
    private String getDefaultDisplayName(String field) {
        // Отделяем префикс сущности, если есть
        int dotIndex = field.lastIndexOf('.');
        if (dotIndex > 0) {
            field = field.substring(dotIndex + 1);
        }

        // Преобразуем camelCase в нормальный текст
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (i == 0) {
                formatted.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                formatted.append(' ').append(c);
            } else {
                formatted.append(c);
            }
        }

        return formatted.toString();
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