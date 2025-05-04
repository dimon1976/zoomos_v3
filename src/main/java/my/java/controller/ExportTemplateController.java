// src/main/java/my/java/controller/ExportTemplateController.java
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

            // Проверяем наличие сохраняемых полей
            if (template.getFields() == null) {
                template.setFields(new ArrayList<>());
            }

            // Собираем параметры файла
            Map<String, String> fileOptions = new HashMap<>();

            // Добавляем основные параметры файла
            fileOptions.put("format", template.getFileType());

            // Парсим параметры файла из формы (с префиксом file_)
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("file_")) {
                    String paramName = entry.getKey().substring("file_".length());
                    fileOptions.put(paramName, entry.getValue());
                    log.debug("Получен параметр файла: {}={}", paramName, entry.getValue());
                }
            }

            // Добавляем параметры без префикса file_ для обратной совместимости
            for (String paramName : new String[]{"delimiter", "quoteChar", "encoding", "sheetName", "autoSizeColumns", "includeHeader"}) {
                if (allParams.containsKey(paramName) && !fileOptions.containsKey(paramName)) {
                    fileOptions.put(paramName, allParams.get(paramName));
                    log.debug("Получен параметр файла без префикса: {}={}", paramName, allParams.get(paramName));
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
                log.debug("Параметры файла сохранены в JSON: {}", fileOptionsJson);
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