// src/main/java/my/java/controller/ExportTemplateController.java
package my.java.controller;

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
                    template.setFileType("csv"); // По умолчанию CSV

                    model.addAttribute("template", template);
                    model.addAttribute("client", client);
                    model.addAttribute("isNew", true);

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

            // Обрабатываем поля шаблона из формы
            List<ExportTemplate.ExportField> fields = new ArrayList<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("field_") && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    String fieldName = entry.getValue();

                    // Создаем поле шаблона
                    ExportTemplate.ExportField exportField = new ExportTemplate.ExportField();
                    exportField.setOriginalField(fieldName);

                    // Ищем заголовок для поля
                    String headerKey = "header_" + fieldName.replace(".", "_");
                    if (allParams.containsKey(headerKey)) {
                        exportField.setDisplayName(allParams.get(headerKey));
                    } else {
                        // Если заголовок не задан, используем имя поля
                        exportField.setDisplayName(fieldName.substring(fieldName.lastIndexOf('.') + 1));
                    }

                    fields.add(exportField);
                }
            }

            // Если поля не были обработаны стандартным путем, используем параметры с префиксом fields[
            if (fields.isEmpty()) {
                Map<Integer, String> originalFields = new HashMap<>();
                Map<Integer, String> displayNames = new HashMap<>();

                for (Map.Entry<String, String> entry : allParams.entrySet()) {
                    if (entry.getKey().startsWith("fields[") && entry.getKey().contains("].originalField")) {
                        int index = extractIndex(entry.getKey(), "fields[", "].originalField");
                        originalFields.put(index, entry.getValue());
                    } else if (entry.getKey().startsWith("fields[") && entry.getKey().contains("].displayName")) {
                        int index = extractIndex(entry.getKey(), "fields[", "].displayName");
                        displayNames.put(index, entry.getValue());
                    }
                }

                // Объединяем данные в поля шаблона
                for (Integer index : originalFields.keySet()) {
                    String originalField = originalFields.get(index);
                    String displayName = displayNames.getOrDefault(index,
                            originalField.substring(originalField.lastIndexOf('.') + 1));

                    ExportTemplate.ExportField exportField = new ExportTemplate.ExportField();
                    exportField.setOriginalField(originalField);
                    exportField.setDisplayName(displayName);
                    fields.add(exportField);
                }
            }

            // Устанавливаем поля в шаблон
            template.setFields(fields);

            // Собираем дополнительные параметры файла
            Map<String, String> fileOptions = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
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
     * Извлекает индекс из строки формата "prefix[index]suffix"
     */
    private int extractIndex(String key, String prefix, String suffix) {
        int startIndex = key.indexOf(prefix) + prefix.length();
        int endIndex = key.indexOf(suffix);
        if (startIndex >= 0 && endIndex > startIndex) {
            try {
                return Integer.parseInt(key.substring(startIndex, endIndex));
            } catch (NumberFormatException e) {
                log.warn("Не удалось извлечь индекс из ключа: {}", key);
            }
        }
        return -1;
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