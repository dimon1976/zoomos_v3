// src/main/java/my/java/controller/ExportTemplateController.java
package my.java.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.ExportTemplate;
import my.java.service.client.ClientService;
import my.java.service.file.exporter.ExportTemplateService;
import my.java.service.file.metadata.EntityMetadata;
import my.java.service.file.metadata.EntityRegistry;
import my.java.service.file.options.FileWritingOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clients/{clientId}/export/templates")
@RequiredArgsConstructor
@Slf4j
public class ExportTemplateController {

    private final ClientService clientService;
    private final ExportTemplateService templateService;
    private final EntityRegistry entityRegistry;

    /**
     * Отображение списка шаблонов
     */
    @GetMapping
    public String listTemplates(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(clientId)
                .map(clientDto -> {
                    model.addAttribute("client", clientDto);
                    List<ExportTemplate> templates = templateService.getAllTemplatesForClient(clientId);
                    model.addAttribute("templates", templates);
                    return "export/templates/list";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент не найден: " + clientId);
                    return "redirect:/clients";
                });
    }

    /**
     * Отображение формы создания/редактирования шаблона
     */
    @GetMapping("/{id}")
    public String editTemplate(@PathVariable Long clientId, @PathVariable Long id,
                               Model model, RedirectAttributes redirectAttributes) {

        return templateService.getTemplateById(id)
                .map(template -> {
                    // Проверяем, принадлежит ли шаблон указанному клиенту
                    if (!template.getClient().getId().equals(clientId)) {
                        redirectAttributes.addFlashAttribute("errorMessage",
                                "Шаблон не принадлежит указанному клиенту");
                        return "redirect:/clients/" + clientId + "/export/templates";
                    }

                    model.addAttribute("template", template);
                    model.addAttribute("client", template.getClient());

                    // Добавляем поля шаблона в модель
                    model.addAttribute("fields", template.getFields());

                    // Добавляем параметры экспорта в модель
                    addExportOptionsToModel(model, template.getExportOptions());

                    // Загружаем метаданные полей для типа сущности шаблона
                    loadEntityFields(model, template.getEntityType());

                    return "export/templates/edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден: " + id);
                    return "redirect:/clients/" + clientId + "/export/templates";
                });
    }

    /**
     * Отображение формы создания нового шаблона
     */
    @GetMapping("/new")
    public String newTemplate(@PathVariable Long clientId,
                              @RequestParam(required = false) String entityType,
                              Model model, RedirectAttributes redirectAttributes) {
        return clientService.findClientEntityById(clientId)
                .map(client -> {
                    // Добавляем список типов сущностей
                    model.addAttribute("entityTypes", entityRegistry.getMainEntities());

                    // Если не указан тип сущности, показываем форму выбора
                    if (entityType == null || entityType.isEmpty()) {
                        model.addAttribute("client", client);
                        model.addAttribute("isNew", true);
                        return "export/templates/choose_entity";
                    }

                    // Создаем пустой шаблон для формы с выбранным типом сущности
                    ExportTemplate template = new ExportTemplate();
                    template.setClient(client);
                    template.setEntityType(entityType);

                    // Создаем настройки экспорта по умолчанию
                    FileWritingOptions options = new FileWritingOptions();
                    options.setFileType("csv"); // По умолчанию CSV
                    template.setExportOptions(options);

                    model.addAttribute("template", template);
                    model.addAttribute("client", client);
                    model.addAttribute("isNew", true);

                    // Добавляем параметры экспорта в модель
                    addExportOptionsToModel(model, options);

                    // Загружаем метаданные полей для выбранного типа сущности
                    loadEntityFields(model, entityType);

                    return "export/templates/edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент не найден: " + clientId);
                    return "redirect:/clients";
                });
    }

    /**
     * Загружает поля сущности и связанных сущностей в модель
     */
    private void loadEntityFields(Model model, String entityType) {
        if (entityType != null && !entityType.isEmpty()) {
            EntityMetadata entityMetadata = entityRegistry.getEntityMetadata(entityType);
            if (entityMetadata != null) {
                // Загружаем поля основной сущности
                model.addAttribute("entityFields", entityMetadata.getPrefixedFields());

                // Загружаем поля связанных сущностей
                List<EntityMetadata> relatedEntities = entityRegistry.getRelatedEntities(entityType);
                if (!relatedEntities.isEmpty()) {
                    Map<String, Map<String, EntityMetadata.FieldMetadata>> relatedFields = new HashMap<>();

                    for (EntityMetadata relatedEntity : relatedEntities) {
                        relatedFields.put(relatedEntity.getEntityType(), relatedEntity.getPrefixedFields());
                    }

                    model.addAttribute("relatedFields", relatedFields);
                }
            }
        }
    }

    /**
     * Добавляет параметры экспорта в модель
     */
    private void addExportOptionsToModel(Model model, FileWritingOptions options) {
        model.addAttribute("fileFormat", options.getFileType());
        model.addAttribute("includeHeader", options.isIncludeHeader());

        // Параметры для CSV
        if (options.getDelimiter() != null) {
            model.addAttribute("delimiter", options.getDelimiter().toString());
        }

        if (options.getQuoteChar() != null) {
            model.addAttribute("quoteChar", options.getQuoteChar().toString());
        }

        if (options.getCharset() != null) {
            model.addAttribute("encoding", options.getCharset().name());
        }

        // Параметры для Excel
        if (options.getSheetName() != null) {
            model.addAttribute("sheetName", options.getSheetName());
        }

        model.addAttribute("autoSizeColumns", options.isAutoSizeColumns());

        // Стратегия
        if (options.getAdditionalParams() != null && options.getAdditionalParams().containsKey("strategyId")) {
            model.addAttribute("strategyId", options.getAdditionalParams().get("strategyId"));
        }
    }

    /**
     * Сохранение шаблона
     */
    @PostMapping("/save")
    public String saveTemplate(@PathVariable Long clientId,
                               @ModelAttribute ExportTemplate template,
                               @RequestParam(value = "isDefault", required = false) Boolean isDefault,
                               @RequestParam(value = "strategyId", required = false) String strategyId,
                               @RequestParam Map<String, String> allParams,
                               RedirectAttributes redirectAttributes) {
        try {
            // Проверяем существование клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + clientId));

            // Устанавливаем флаг "по умолчанию"
            template.setDefault(Boolean.TRUE.equals(isDefault));

            // Устанавливаем клиента
            template.setClient(client);

            // Устанавливаем стратегию
            template.setStrategyId(strategyId);

            // Проверяем наличие сохраняемых полей
            if (template.getFields() == null) {
                template.setFields(new ArrayList<>());
            }

            // Создаем и заполняем настройки экспорта из параметров формы
            FileWritingOptions options = createOptionsFromParams(allParams);

            // Если стратегия указана, добавляем ее в параметры
            if (strategyId != null && !strategyId.isEmpty()) {
                options.getAdditionalParams().put("strategyId", strategyId);
            }

            template.setExportOptions(options);

            // Сохраняем или обновляем шаблон
            if (template.getId() == null) {
                // Это новый шаблон
                templateService.saveTemplate(template);
                redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно создан");
            } else {
                // Это существующий шаблон
                templateService.updateTemplate(template.getId(), template);
                redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно обновлен");
            }

            return "redirect:/clients/" + clientId + "/export/templates";
        } catch (Exception e) {
            log.error("Ошибка при сохранении шаблона: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates";
        }
    }

    /**
     * Сохранение шаблона из формы экспорта
     */
    @PostMapping("/save-from-export")
    public String saveTemplateFromExport(
            @PathVariable Long clientId,
            @RequestParam("templateName") String templateName,
            @RequestParam(value = "templateId", required = false) Long templateId,
            @RequestParam("entityType") String entityType,
            @RequestParam("format") String format,
            @RequestParam(value = "strategyId", required = false) String strategyId,
            @RequestParam(value = "fields", required = false) List<String> fields,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) { // Добавляем HttpServletRequest для получения всех параметров

        log.info("========== НАЧАЛО ОБРАБОТКИ ЗАПРОСА SAVE-FROM-EXPORT ==========");
        log.info("Получен запрос на сохранение шаблона из формы экспорта:");
        log.info("Client ID: {}", clientId);
        log.info("Template Name: {}", templateName);
        log.info("Template ID: {} (тип: {})", templateId, templateId != null ? templateId.getClass().getName() : "null");
        log.info("Entity Type: {}", entityType);
        log.info("Format: {}", format);
        log.info("Strategy ID: {}", strategyId);
        log.info("Fields count: {}", fields != null ? fields.size() : 0);

        // Логирование всех параметров запроса
        log.info("Все параметры запроса:");
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            log.info("Параметр '{}': {}", entry.getKey(),
                    entry.getValue() != null ? Arrays.toString(entry.getValue()) : "null");
        }

        try {
            // Проверяем режим работы (создание или обновление)
            boolean isUpdate = templateId != null;
            log.info("Режим работы: {}", isUpdate ? "Обновление существующего шаблона" : "Создание нового шаблона");

            // Проверяем существование клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + clientId));
            log.info("Клиент найден: {}", client.getName());

            // Проверяем наличие полей
            if (fields == null || fields.isEmpty()) {
                log.error("Не выбраны поля для шаблона");
                throw new IllegalArgumentException("Необходимо выбрать хотя бы одно поле для шаблона");
            }
            log.info("Выбрано {} полей для шаблона", fields.size());

            ExportTemplate template;

            // Проверяем, обновляем ли мы существующий шаблон
            if (isUpdate) {
                log.info("Обновляем существующий шаблон с ID: {}", templateId);
                template = templateService.getTemplateById(templateId)
                        .orElseThrow(() -> new IllegalArgumentException("Шаблон с ID " + templateId + " не найден"));
                log.info("Существующий шаблон найден: {}", template.getName());

                // Проверяем, принадлежит ли шаблон клиенту
                if (!template.getClient().getId().equals(clientId)) {
                    log.error("Шаблон с ID {} не принадлежит клиенту {} (принадлежит клиенту {})",
                            templateId, clientId, template.getClient().getId());
                    throw new IllegalArgumentException("Шаблон не принадлежит указанному клиенту");
                }
                log.info("Проверка принадлежности шаблона клиенту пройдена");

                // Сохраняем существующие поля, если новые поля не переданы
                if (fields.isEmpty() && !template.getFields().isEmpty()) {
                    log.warn("Не получены новые поля, сохраняем существующие ({} полей)", template.getFields().size());
                    fields = template.getFields().stream()
                            .map(ExportTemplate.ExportField::getOriginalField)
                            .collect(Collectors.toList());
                }
            } else {
                // Создаем новый шаблон
                log.info("Создаем новый шаблон");
                template = new ExportTemplate();
                template.setClient(client);
                template.setEntityType(entityType);
            }

            // Обновляем основные поля шаблона
            template.setName(templateName);
            template.setStrategyId(strategyId);
            log.info("Установлены основные поля шаблона: имя='{}', стратегия='{}'", templateName, strategyId);

            // Создаем настройки экспорта из параметров
            FileWritingOptions options = createOptionsFromParams(allParams);
            options.setFileType(format);
            log.info("Создан объект FileWritingOptions с типом: {}", format);

            // Добавляем стратегию в параметры, если указана
            if (strategyId != null && !strategyId.isEmpty()) {
                options.getAdditionalParams().put("strategyId", strategyId);
                log.info("Добавлена стратегия в дополнительные параметры: {}", strategyId);
            }

            // Устанавливаем настройки экспорта
            template.setExportOptions(options);
            log.info("Настройки экспорта установлены в шаблон");

            // Обновляем поля шаблона
            if (!fields.isEmpty()) {
                log.info("Начинаем обновление полей шаблона ({} полей)", fields.size());
                List<ExportTemplate.ExportField> exportFields = new ArrayList<>();
                for (String field : fields) {
                    ExportTemplate.ExportField exportField = new ExportTemplate.ExportField();
                    exportField.setOriginalField(field);

                    // Проверяем, задан ли пользовательский заголовок
                    String headerParam = "header_" + field.replace(".", "_");
                    if (allParams.containsKey(headerParam)) {
                        exportField.setDisplayName(allParams.get(headerParam));
                        log.debug("Установлен пользовательский заголовок для поля {}: {}",
                                field, allParams.get(headerParam));
                    } else {
                        // Проверяем, существует ли заголовок в текущих полях шаблона
                        if (isUpdate) {
                            Optional<ExportTemplate.ExportField> existingField = template.getFields().stream()
                                    .filter(f -> f.getOriginalField().equals(field))
                                    .findFirst();

                            if (existingField.isPresent()) {
                                exportField.setDisplayName(existingField.get().getDisplayName());
                                log.debug("Использован существующий заголовок для поля {}: {}",
                                        field, existingField.get().getDisplayName());
                            } else {
                                exportField.setDisplayName(getDefaultDisplayName(field));
                                log.debug("Установлен заголовок по умолчанию для поля {}: {}",
                                        field, getDefaultDisplayName(field));
                            }
                        } else {
                            exportField.setDisplayName(getDefaultDisplayName(field));
                            log.debug("Установлен заголовок по умолчанию для поля {}: {}",
                                    field, getDefaultDisplayName(field));
                        }
                    }

                    exportFields.add(exportField);
                }

                // Устанавливаем новые поля
                template.setFields(exportFields);
                log.info("Установлено {} полей в шаблоне", exportFields.size());
            } else {
                log.warn("Не установлены поля для шаблона!");
            }

            // Сохраняем шаблон
            if (isUpdate) {
                log.info("Вызов метода updateTemplate с ID: {}", templateId);
                ExportTemplate updatedTemplate = templateService.updateTemplate(templateId, template);

                // Принудительно загружаем данные из JSON после обновления
                updatedTemplate.loadFromJson();

                log.info("Шаблон с ID {} успешно обновлен. Содержит {} полей",
                        templateId, updatedTemplate.getFields().size());
                redirectAttributes.addFlashAttribute("successMessage",
                        "Шаблон '" + templateName + "' успешно обновлен");
            } else {
                log.info("Вызов метода saveTemplate для создания нового шаблона");
                ExportTemplate savedTemplate = templateService.saveTemplate(template);

                // Принудительно загружаем данные из JSON после сохранения
                savedTemplate.loadFromJson();

                log.info("Новый шаблон успешно создан с ID: {}. Содержит {} полей",
                        savedTemplate.getId(), savedTemplate.getFields().size());
                redirectAttributes.addFlashAttribute("successMessage",
                        "Шаблон '" + templateName + "' успешно сохранен");
            }

            log.info("========== КОНЕЦ ОБРАБОТКИ ЗАПРОСА SAVE-FROM-EXPORT ==========");
            // Возвращаемся на страницу экспорта
            return "redirect:/clients/" + clientId + "/export?entityType=" + entityType;

        } catch (Exception e) {
            log.error("Ошибка при сохранении шаблона из формы экспорта: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            log.info("========== КОНЕЦ ОБРАБОТКИ ЗАПРОСА SAVE-FROM-EXPORT (С ОШИБКОЙ) ==========");
            return "redirect:/clients/" + clientId + "/export?entityType=" + entityType;
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
                options.setCharset(java.nio.charset.Charset.forName(params.get("encoding")));
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
            if (entry.getKey().startsWith("strategy_") && !entry.getValue().isEmpty()) {
                options.getAdditionalParams().put(entry.getKey(), entry.getValue());
            }
        }

        return options;
    }

    /**
     * Удаление шаблона
     */
    @PostMapping("/{id}/delete")
    public String deleteTemplate(@PathVariable Long clientId, @PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {

        try {
            // Проверяем, существует ли шаблон и принадлежит ли он клиенту
            templateService.getTemplateById(id)
                    .filter(template -> template.getClient().getId().equals(clientId))
                    .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден или не принадлежит клиенту"));

            templateService.deleteTemplate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении шаблона: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
        }

        return "redirect:/clients/" + clientId + "/export/templates";
    }

    /**
     * Удаление поля из шаблона
     */
    @PostMapping("/{id}/remove-field")
    public String removeField(@PathVariable Long clientId,
                              @PathVariable Long id,
                              @RequestParam("fieldIndex") int fieldIndex,
                              RedirectAttributes redirectAttributes) {
        try {
            // Получаем шаблон
            Optional<ExportTemplate> templateOpt = templateService.getTemplateById(id);
            if (templateOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден");
                return "redirect:/clients/" + clientId + "/export/templates";
            }

            ExportTemplate template = templateOpt.get();

            // Проверяем, что шаблон принадлежит клиенту
            if (!template.getClient().getId().equals(clientId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не принадлежит данному клиенту");
                return "redirect:/clients/" + clientId + "/export/templates";
            }

            // Проверяем, что индекс валидный
            if (fieldIndex < 0 || fieldIndex >= template.getFields().size()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Неверный индекс поля");
                return "redirect:/clients/" + clientId + "/export/templates/" + id;
            }

            // Удаляем поле
            template.getFields().remove(fieldIndex);

            // Сохраняем шаблон
            templateService.updateTemplate(id, template);

            redirectAttributes.addFlashAttribute("successMessage", "Поле успешно удалено из шаблона");
        } catch (Exception e) {
            log.error("Ошибка при удалении поля из шаблона: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
        }

        return "redirect:/clients/" + clientId + "/export/templates/" + id;
    }

    /**
     * Установка шаблона по умолчанию
     */
    @PostMapping("/{id}/set-default")
    public String setDefaultTemplate(@PathVariable Long clientId, @PathVariable Long id,
                                     RedirectAttributes redirectAttributes) {
        try {
            // Проверяем, существует ли шаблон и принадлежит ли он клиенту
            templateService.getTemplateById(id)
                    .filter(template -> template.getClient().getId().equals(clientId))
                    .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден или не принадлежит клиенту"));

            templateService.setDefaultTemplate(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон успешно установлен как шаблон по умолчанию");
        } catch (Exception e) {
            log.error("Ошибка при установке шаблона по умолчанию: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
        }

        return "redirect:/clients/" + clientId + "/export/templates";
    }

    /**
     * Получение отображаемого имени поля по умолчанию
     */
    private String getDefaultDisplayName(String field) {
        // Отделяем префикс сущности, если есть
        int dotIndex = field.lastIndexOf('.');
        if (dotIndex > 0) {
            field = field.substring(dotIndex + 1);
        }

        // Преобразуем camelCase в читаемый текст
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
     * API для загрузки доступных шаблонов
     */
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<?> getTemplatesForEntity(@PathVariable Long clientId,
                                                   @RequestParam String entityType) {
        try {
            List<ExportTemplate> templates = templateService.getTemplatesForEntityType(clientId, entityType);

            // Формируем упрощенный список для API
            List<Map<String, Object>> simplifiedTemplates = templates.stream()
                    .map(template -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", template.getId());
                        map.put("name", template.getName());
                        map.put("isDefault", template.isDefault());
                        map.put("fileType", template.getFileType());
                        map.put("fieldsCount", template.getFields().size());
                        map.put("updatedAt", template.getUpdatedAt());
                        return map;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(simplifiedTemplates);
        } catch (Exception e) {
            log.error("Ошибка при загрузке шаблонов: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * API для загрузки шаблона по ID
     */
    @GetMapping("/api/{templateId}")
    @ResponseBody
    public ResponseEntity<?> getTemplateById(@PathVariable Long clientId,
                                             @PathVariable Long templateId) {
        try {
            return templateService.getTemplateById(templateId)
                    .filter(template -> template.getClient().getId().equals(clientId))
                    .map(template -> {
                        // Создаем объект для API с нужными полями
                        Map<String, Object> result = new HashMap<>();
                        result.put("id", template.getId());
                        result.put("name", template.getName());
                        result.put("entityType", template.getEntityType());
                        result.put("fileType", template.getFileType());
                        result.put("strategyId", template.getStrategyId() != null ? template.getStrategyId() : "");
                        result.put("isDefault", template.isDefault());
                        result.put("fields", template.getFields());
                        result.put("exportOptions", template.getExportOptions());

                        return ResponseEntity.ok(result);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Ошибка при загрузке шаблона: {}", e.getMessage(), e);
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMap);
        }
    }
}