// src/main/java/my/java/controller/ExportController.java
package my.java.controller;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            @RequestParam(value = "saveAsTemplate", required = false) Boolean saveAsTemplate,
            @RequestParam(value = "templateName", required = false) String templateName,
            @RequestParam(value = "strategyId", required = false) String strategyId,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        log.info("POST запрос на экспорт данных для клиента: {}, тип сущности: {}, формат: {}",
                clientId, entityType, format);

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

            // Сохраняем параметры файла в JSON
            Map<String, String> fileOptions = new HashMap<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey().startsWith("file_")) {
                    fileOptions.put(entry.getKey().substring(5), entry.getValue());
                }
            }

            // TODO: сериализовать fileOptions в JSON и сохранить

            // Сохраняем шаблон
            templateService.saveTemplate(template);

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
                return MediaType.parseMediaType("text/csv");
            case "xlsx":
                return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls":
                return MediaType.parseMediaType("application/vnd.ms-excel");
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}