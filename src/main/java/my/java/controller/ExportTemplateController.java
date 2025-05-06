package my.java.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.ExportTemplate;
import my.java.service.client.ClientService;
import my.java.service.file.exporter.ExportTemplateService;
import my.java.service.file.metadata.EntityMetadata;
import my.java.service.file.metadata.EntityRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZonedDateTime;
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
                               @RequestParam(required = false) String format,
                               Model model, RedirectAttributes redirectAttributes) {

        return templateService.getTemplateById(id)
                .map(template -> {
                    // Проверяем, принадлежит ли шаблон указанному клиенту
                    if (!template.getClient().getId().equals(clientId)) {
                        redirectAttributes.addFlashAttribute("errorMessage",
                                "Шаблон не принадлежит указанному клиенту");
                        return "redirect:/clients/" + clientId + "/export/templates";
                    }

                    // Применяем формат файла, если передан в параметрах
                    if (format != null && !format.isEmpty()) {
                        template.setFileType(format);
                    }

                    model.addAttribute("template", template);
                    model.addAttribute("client", template.getClient());

                    // Добавляем поля шаблона в модель
                    model.addAttribute("fields", template.getFields());

                    // Загружаем метаданные полей для типа сущности шаблона
                    loadEntityFields(model, template.getEntityType());

                    // Подготавливаем параметры файла
                    prepareFileOptions(model, template);

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
                    model.addAttribute("client", client);

                    // Если не указан тип сущности, показываем форму выбора
                    if (entityType == null || entityType.isEmpty()) {
                        model.addAttribute("isNew", true);
                        return "export/templates/choose_entity";
                    }

                    // Создаем пустой шаблон для формы с выбранным типом сущности
                    ExportTemplate template = new ExportTemplate();
                    template.setClient(client);
                    template.setEntityType(entityType);
                    template.setFileType("csv"); // По умолчанию CSV

                    model.addAttribute("template", template);
                    model.addAttribute("isNew", true);

                    // Загружаем метаданные полей для выбранного типа сущности
                    loadEntityFields(model, entityType);

                    // Подготавливаем параметры файла по умолчанию
                    prepareDefaultFileOptions(model);

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

                // Получаем имена выбранных полей в виде простого списка для easy-check в шаблоне
                ExportTemplate template = (ExportTemplate) model.getAttribute("template");
                if (template != null && template.getFields() != null) {
                    Set<String> selectedFieldNames = template.getFields().stream()
                            .map(ExportTemplate.ExportField::getOriginalField)
                            .collect(Collectors.toSet());
                    model.addAttribute("selectedFieldNames", selectedFieldNames);
                } else {
                    model.addAttribute("selectedFieldNames", Collections.emptySet());
                }
            }
        }
    }

        /**
         * Подготавливает параметры файла из шаблона
         */
    private void prepareFileOptions(Model model, ExportTemplate template) {
        Map<String, String> fileOptions = template.getFileOptionsAsMap();

        // Подготавливаем параметры для CSV
        String delimiter = fileOptions.getOrDefault("delimiter", ",");
        String quoteChar = fileOptions.getOrDefault("quoteChar", "\"");
        String encoding = fileOptions.getOrDefault("encoding", "UTF-8");

        // Подготавливаем параметры для Excel
        String sheetName = fileOptions.getOrDefault("sheetName", "Data");
        boolean autoSizeColumns = Boolean.parseBoolean(fileOptions.getOrDefault("autoSizeColumns", "true"));

        // Общие параметры
        boolean includeHeader = Boolean.parseBoolean(fileOptions.getOrDefault("includeHeader", "true"));

        // Добавляем параметры в модель
        model.addAttribute("delimiter", delimiter);
        model.addAttribute("quoteChar", quoteChar);
        model.addAttribute("encoding", encoding);
        model.addAttribute("sheetName", sheetName);
        model.addAttribute("autoSizeColumns", autoSizeColumns);
        model.addAttribute("includeHeader", includeHeader);
        model.addAttribute("fileOptions", fileOptions);
    }

    /**
     * Подготавливает параметры файла по умолчанию
     */
    private void prepareDefaultFileOptions(Model model) {
        model.addAttribute("delimiter", ",");
        model.addAttribute("quoteChar", "\"");
        model.addAttribute("encoding", "UTF-8");
        model.addAttribute("sheetName", "Data");
        model.addAttribute("autoSizeColumns", true);
        model.addAttribute("includeHeader", true);
        model.addAttribute("fileOptions", new HashMap<String, String>());
    }

    /**
     * Смена формата файла
     */
    @PostMapping("/{id}/change-format")
    public String changeFileFormat(@PathVariable Long clientId,
                                   @PathVariable Long id,
                                   @RequestParam String format,
                                   RedirectAttributes redirectAttributes) {

        try {
            Optional<ExportTemplate> templateOpt = templateService.getTemplateById(id);
            if (templateOpt.isEmpty() || !templateOpt.get().getClient().getId().equals(clientId)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Шаблон не найден или не принадлежит данному клиенту");
                return "redirect:/clients/" + clientId + "/export/templates";
            }

            return "redirect:/clients/" + clientId + "/export/templates/" + id + "?format=" + format;
        } catch (Exception e) {
            log.error("Ошибка при смене формата файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        }
    }

    /**
     * Выбор поля шаблона
     */
    @PostMapping("/{id}/add-field")
    public String addField(@PathVariable Long clientId,
                           @PathVariable Long id,
                           @RequestParam String fieldName,
                           @RequestParam String displayName,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        try {
            log.info("Добавление поля в шаблон: clientId={}, templateId={}, fieldName={}, displayName={}",
                    clientId, id, fieldName, displayName);

            // Получаем клиента для проверки
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Получаем шаблон
            ExportTemplate template = templateService.getTemplateById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Шаблон с ID " + id + " не найден"));

            // Проверка принадлежности шаблона клиенту
            if (!template.getClient().getId().equals(clientId)) {
                log.warn("Шаблон с ID {} не принадлежит клиенту {}", id, clientId);
                redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не принадлежит данному клиенту");
                return "redirect:/clients/" + clientId + "/export/templates";
            }

            // Инициализация списка полей, если он null
            if (template.getFields() == null) {
                template.setFields(new ArrayList<>());
            }

            // Проверяем, есть ли уже такое поле
            boolean fieldExists = false;
            for (ExportTemplate.ExportField field : template.getFields()) {
                if (field.getOriginalField().equals(fieldName)) {
                    fieldExists = true;
                    break;
                }
            }

            if (!fieldExists) {
                // Создаем новое поле
                ExportTemplate.ExportField field = new ExportTemplate.ExportField();
                field.setOriginalField(fieldName);
                field.setDisplayName(displayName);

                // Добавляем поле в список
                template.getFields().add(field);

                // Сохраняем шаблон
                log.info("Добавляем поле {} в шаблон {}", fieldName, template.getName());

                // Обновляем время изменения
                template.setUpdatedAt(ZonedDateTime.now());

                // Сохраняем шаблон напрямую
                ExportTemplate savedTemplate = templateService.saveTemplate(template);

                log.info("Шаблон успешно обновлен, новое количество полей: {}", savedTemplate.getFields().size());
                redirectAttributes.addFlashAttribute("successMessage",
                        "Поле '" + displayName + "' добавлено в шаблон");
            } else {
                log.info("Поле {} уже существует в шаблоне {}", fieldName, template.getName());
                redirectAttributes.addFlashAttribute("infoMessage",
                        "Поле '" + displayName + "' уже присутствует в шаблоне");
            }

            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        } catch (Exception e) {
            log.error("Ошибка при добавлении поля в шаблон: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        }
    }

    /**
     * Удаление поля из шаблона
     */
    @PostMapping("/{id}/remove-field")
    public String removeField(@PathVariable Long clientId,
                              @PathVariable Long id,
                              @RequestParam String fieldName,
                              RedirectAttributes redirectAttributes) {
        try {
            Optional<ExportTemplate> templateOpt = templateService.getTemplateById(id);
            if (templateOpt.isEmpty() || !templateOpt.get().getClient().getId().equals(clientId)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Шаблон не найден или не принадлежит данному клиенту");
                return "redirect:/clients/" + clientId + "/export/templates";
            }

            ExportTemplate template = templateOpt.get();

            // Находим поле для удаления
            Optional<ExportTemplate.ExportField> fieldOpt = template.getFields().stream()
                    .filter(f -> f.getOriginalField().equals(fieldName))
                    .findFirst();

            if (fieldOpt.isPresent()) {
                template.getFields().remove(fieldOpt.get());
                templateService.updateTemplate(id, template);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Поле '" + fieldOpt.get().getDisplayName() + "' удалено из шаблона");
            } else {
                redirectAttributes.addFlashAttribute("infoMessage",
                        "Поле не найдено в шаблоне");
            }

            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        } catch (Exception e) {
            log.error("Ошибка при удалении поля из шаблона: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        }
    }

    /**
     * Изменение порядка полей (для обработки формы с сортировкой через JS)
     */
    @PostMapping("/{id}/update-fields-order")
    public String updateFieldsOrder(@PathVariable Long clientId,
                                    @PathVariable Long id,
                                    @RequestParam("fieldOrder") List<String> fieldOrder,
                                    RedirectAttributes redirectAttributes) {
        try {
            Optional<ExportTemplate> templateOpt = templateService.getTemplateById(id);
            if (templateOpt.isEmpty() || !templateOpt.get().getClient().getId().equals(clientId)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Шаблон не найден или не принадлежит данному клиенту");
                return "redirect:/clients/" + clientId + "/export/templates";
            }

            ExportTemplate template = templateOpt.get();

            // Создаем новый список полей в указанном порядке
            List<ExportTemplate.ExportField> orderedFields = new ArrayList<>();

            for (String fieldName : fieldOrder) {
                template.getFields().stream()
                        .filter(f -> f.getOriginalField().equals(fieldName))
                        .findFirst()
                        .ifPresent(orderedFields::add);
            }

            // Добавляем поля, которые не были в списке порядка
            template.getFields().stream()
                    .filter(f -> !orderedFields.contains(f))
                    .forEach(orderedFields::add);

            template.setFields(orderedFields);
            templateService.updateTemplate(id, template);

            redirectAttributes.addFlashAttribute("successMessage", "Порядок полей обновлен");

            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        } catch (Exception e) {
            log.error("Ошибка при обновлении порядка полей: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        }
    }

    /**
     * Выбор всех полей
     */
    @PostMapping("/{id}/select-all-fields")
    public String selectAllFields(@PathVariable Long clientId,
                                  @PathVariable Long id,
                                  @RequestParam(required = false) String entityType,
                                  RedirectAttributes redirectAttributes) {
        try {
            Optional<ExportTemplate> templateOpt = templateService.getTemplateById(id);
            if (templateOpt.isEmpty() || !templateOpt.get().getClient().getId().equals(clientId)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Шаблон не найден или не принадлежит данному клиенту");
                return "redirect:/clients/" + clientId + "/export/templates";
            }

            ExportTemplate template = templateOpt.get();
            entityType = entityType != null ? entityType : template.getEntityType();

            // Получаем все поля основной сущности
            EntityMetadata entityMetadata = entityRegistry.getEntityMetadata(entityType);
            if (entityMetadata != null) {
                // Сохраняем текущие поля для проверки дубликатов
                Set<String> existingFields = template.getFields().stream()
                        .map(ExportTemplate.ExportField::getOriginalField)
                        .collect(Collectors.toSet());

                // Добавляем поля основной сущности
                for (Map.Entry<String, EntityMetadata.FieldMetadata> entry :
                        entityMetadata.getPrefixedFields().entrySet()) {
                    String fieldName = entry.getKey();
                    if (!existingFields.contains(fieldName)) {
                        ExportTemplate.ExportField field = new ExportTemplate.ExportField();
                        field.setOriginalField(fieldName);
                        field.setDisplayName(entry.getValue().getDisplayName());
                        template.getFields().add(field);
                        existingFields.add(fieldName);
                    }
                }

                // Добавляем поля связанных сущностей
                List<EntityMetadata> relatedEntities = entityRegistry.getRelatedEntities(entityType);
                for (EntityMetadata relatedEntity : relatedEntities) {
                    for (Map.Entry<String, EntityMetadata.FieldMetadata> entry :
                            relatedEntity.getPrefixedFields().entrySet()) {
                        String fieldName = entry.getKey();
                        if (!existingFields.contains(fieldName)) {
                            ExportTemplate.ExportField field = new ExportTemplate.ExportField();
                            field.setOriginalField(fieldName);
                            field.setDisplayName(entry.getValue().getDisplayName());
                            template.getFields().add(field);
                            existingFields.add(fieldName);
                        }
                    }
                }

                templateService.updateTemplate(id, template);

                redirectAttributes.addFlashAttribute("successMessage",
                        "Все доступные поля добавлены в шаблон");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Не удалось найти метаданные для типа сущности: " + entityType);
            }

            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        } catch (Exception e) {
            log.error("Ошибка при выборе всех полей: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        }
    }

    /**
     * Снятие выбора со всех полей
     */
    @PostMapping("/{id}/clear-fields")
    public String clearFields(@PathVariable Long clientId,
                              @PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            Optional<ExportTemplate> templateOpt = templateService.getTemplateById(id);
            if (templateOpt.isEmpty() || !templateOpt.get().getClient().getId().equals(clientId)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Шаблон не найден или не принадлежит данному клиенту");
                return "redirect:/clients/" + clientId + "/export/templates";
            }

            ExportTemplate template = templateOpt.get();
            template.getFields().clear();

            templateService.updateTemplate(id, template);

            redirectAttributes.addFlashAttribute("successMessage", "Все поля удалены из шаблона");

            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        } catch (Exception e) {
            log.error("Ошибка при очистке полей шаблона: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates/" + id;
        }
    }

    /**
     * Сохранение шаблона
     */
    @PostMapping("/save")
    public String saveTemplate(@PathVariable Long clientId,
                               @ModelAttribute ExportTemplate template,
                               @RequestParam(value = "isDefault", required = false) Boolean isDefault,
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

            // Проверяем наличие сохраняемых полей
            if (template.getFields() == null) {
                template.setFields(new ArrayList<>());
            }

            // Собираем параметры файла
            Map<String, String> fileOptions = new HashMap<>();

            // Добавляем основные параметры файла
            fileOptions.put("format", template.getFileType());

            // Парсим параметры файла из формы
            for (String paramName : new String[]{"delimiter", "quoteChar", "encoding",
                    "sheetName", "autoSizeColumns", "includeHeader"}) {
                if (allParams.containsKey(paramName)) {
                    fileOptions.put(paramName, allParams.get(paramName));
                }
            }

            // Устанавливаем параметры стратегии
            if (template.getStrategyId() != null && !template.getStrategyId().isEmpty()) {
                fileOptions.put("strategyId", template.getStrategyId());
            }

            // Сохраняем параметры файла как JSON
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String fileOptionsJson = objectMapper.writeValueAsString(fileOptions);
                template.setFileOptions(fileOptionsJson);
            } catch (JsonProcessingException e) {
                log.warn("Не удалось сохранить параметры файла как JSON: {}", e.getMessage());
            }

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
                        result.put("fileOptions", template.getFileOptionsAsMap());

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