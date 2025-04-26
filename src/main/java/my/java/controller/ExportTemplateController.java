package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.export.ExportTemplate;
import my.java.service.client.ClientService;
import my.java.service.export.ExportTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер для управления шаблонами экспорта
 */
@Controller
@RequestMapping("/api/export-templates")
@RequiredArgsConstructor
@Slf4j
public class ExportTemplateController {

    private final ExportTemplateService templateService;
    private final ClientService clientService;

    /**
     * Получение списка шаблонов для клиента
     */
    @GetMapping
    @ResponseBody
    public ResponseEntity<?> getTemplates(
            @RequestParam Long clientId,
            @RequestParam(required = false) String entityType) {

        try {
            List<ExportTemplate> templates;

            if (entityType != null && !entityType.isEmpty()) {
                templates = templateService.getTemplatesForClientAndEntityType(clientId, entityType);
            } else {
                templates = templateService.getRecentTemplates(clientId);
            }

            // Преобразуем в DTO для отправки на клиент
            List<Map<String, Object>> templateDtos = templates.stream()
                    .map(this::convertTemplateToDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("templates", templateDtos));

        } catch (Exception e) {
            log.error("Ошибка при получении шаблонов: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Получение шаблона по ID
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getTemplate(@PathVariable Long id) {
        try {
            return templateService.getTemplateById(id)
                    .map(template -> ResponseEntity.ok(convertTemplateToDto(template)))
                    .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            log.error("Ошибка при получении шаблона: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Создание нового шаблона
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<?> createTemplate(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("clientId") Long clientId,
            @RequestParam("entityType") String entityType,
            @RequestParam("format") String format,
            @RequestParam Map<String, String> fieldMapping,
            @RequestParam Map<String, String> allParams) {

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Фильтруем параметры запроса для получения маппинга полей
            Map<String, String> actualFieldMapping = new HashMap<>();
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                if (entry.getKey().startsWith("field.")) {
                    String sourceField = entry.getKey().substring("field.".length());
                    actualFieldMapping.put(sourceField, entry.getValue());
                }
            }

            // Извлекаем дополнительные параметры
            Map<String, String> params = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("params.")) {
                    String paramName = entry.getKey().substring("params.".length());
                    params.put(paramName, entry.getValue());
                }
            }

            // Получаем условие фильтрации
            String filterCondition = allParams.get("filterCondition");

            // Создаем шаблон
            ExportTemplate template = templateService.createTemplate(
                    name, description, client, entityType, format,
                    actualFieldMapping, params, filterCondition);

            return ResponseEntity.ok(Map.of(
                    "id", template.getId(),
                    "message", "Шаблон успешно создан"
            ));

        } catch (Exception e) {
            log.error("Ошибка при создании шаблона: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Обновление шаблона
     */
    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> updateTemplate(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("format") String format,
            @RequestParam Map<String, String> fieldMapping,
            @RequestParam Map<String, String> allParams) {

        try {
            // Фильтруем параметры запроса для получения маппинга полей
            Map<String, String> actualFieldMapping = new HashMap<>();
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                if (entry.getKey().startsWith("field.")) {
                    String sourceField = entry.getKey().substring("field.".length());
                    actualFieldMapping.put(sourceField, entry.getValue());
                }
            }

            // Извлекаем дополнительные параметры
            Map<String, String> params = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("params.")) {
                    String paramName = entry.getKey().substring("params.".length());
                    params.put(paramName, entry.getValue());
                }
            }

            // Получаем условие фильтрации
            String filterCondition = allParams.get("filterCondition");

            // Обновляем шаблон
            ExportTemplate template = templateService.updateTemplate(
                    id, name, description, format, actualFieldMapping, params, filterCondition);

            return ResponseEntity.ok(Map.of(
                    "id", template.getId(),
                    "message", "Шаблон успешно обновлен"
            ));

        } catch (Exception e) {
            log.error("Ошибка при обновлении шаблона: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Удаление шаблона
     */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id) {
        try {
            templateService.deleteTemplate(id);
            return ResponseEntity.ok(Map.of("message", "Шаблон успешно удален"));

        } catch (Exception e) {
            log.error("Ошибка при удалении шаблона: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Конвертирует шаблон в DTO
     */
    private Map<String, Object> convertTemplateToDto(ExportTemplate template) {
        Map<String, Object> dto = new HashMap<>();

        dto.put("id", template.getId());
        dto.put("name", template.getName());
        dto.put("description", template.getDescription());
        dto.put("clientId", template.getClient().getId());
        dto.put("entityType", template.getEntityType());
        dto.put("format", template.getFormat());
        dto.put("fieldMapping", template.getFieldMapping());
        dto.put("parameters", template.getParameters());
        dto.put("filterCondition", template.getFilterCondition());
        dto.put("createdAt", template.getCreatedAt());
        dto.put("updatedAt", template.getUpdatedAt());
        dto.put("lastUsedAt", template.getLastUsedAt());

        return dto;
    }
}