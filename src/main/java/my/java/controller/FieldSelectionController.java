package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.service.client.ClientService;
import my.java.service.file.mapping.FieldMappingServiceEnhanced;
import my.java.service.file.metadata.EntityMetadata;
import my.java.service.file.metadata.EntityRegistry;
import my.java.service.file.options.FileReadingOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы с выбором полей при импорте/экспорте данных
 */
@Controller
@RequestMapping("/api/fields")
@RequiredArgsConstructor
@Slf4j
public class FieldSelectionController {

    private final EntityRegistry entityRegistry;
    private final FieldMappingServiceEnhanced fieldMappingService;
    private final ClientService clientService;

    /**
     * Получение метаданных полей для одиночной сущности
     */
    @GetMapping("/entity/{entityType}")
    @ResponseBody
    public ResponseEntity<?> getEntityFields(@PathVariable String entityType) {
        try {
            log.debug("Запрос на получение полей для сущности: {}", entityType);

            var entityMetadata = entityRegistry.getEntityMetadata(entityType);
            if (entityMetadata == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Неизвестный тип сущности: " + entityType));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("entityType", entityType);
            result.put("displayName", entityMetadata.getDisplayName());
            result.put("fields", entityMetadata.getPrefixedFields());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка при получении полей сущности: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Получение метаданных полей для составной сущности
     */
    @GetMapping("/composite/{mainEntityType}")
    @ResponseBody
    public ResponseEntity<?> getCompositeEntityFields(
            @PathVariable String mainEntityType,
            @RequestParam(required = false) Map<String, String> params) {
        try {
            log.debug("Запрос на получение полей для составной сущности: {}", mainEntityType);

            // Создаем объект FileReadingOptions из параметров запроса
            FileReadingOptions options = FileReadingOptions.fromMap(params);
            options.getAdditionalParams().put("entityType", mainEntityType);

            // Получаем информацию о связанных сущностях
            List<String> relatedEntities = entityRegistry.getRelatedEntities(mainEntityType)
                    .stream()
                    .map(EntityMetadata::getEntityType)
                    .toList();

            if (!relatedEntities.isEmpty()) {
                String relatedEntitiesStr = String.join(",", relatedEntities);
                options.getAdditionalParams().put("relatedEntities", relatedEntitiesStr);
            }

            // Используем новый метод с поддержкой FileReadingOptions
            var metadata = fieldMappingService.getCompositeEntityFieldsMetadataWithOptions(mainEntityType, options);
            if (metadata.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Неизвестный тип основной сущности: " + mainEntityType));
            }

            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            log.error("Ошибка при получении полей составной сущности: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Получение доступных маппингов полей для клиента и типа сущности
     */
    @GetMapping("/mappings")
    @ResponseBody
    public ResponseEntity<?> getAvailableMappings(
            @RequestParam Long clientId,
            @RequestParam String entityType,
            @RequestParam(defaultValue = "false") boolean composite) {
        try {
            log.debug("Запрос на получение доступных маппингов для клиента: {}, тип: {}, составной: {}",
                    clientId, entityType, composite);

            // Проверяем существование клиента
            var clientOpt = clientService.getClientById(clientId);
            if (clientOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Клиент не найден: " + clientId));
            }

            var mappings = fieldMappingService.getAvailableMappingsForClient(clientId, entityType);

            return ResponseEntity.ok(Map.of("mappings", mappings));
        } catch (Exception e) {
            log.error("Ошибка при получении доступных маппингов: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Создание нового маппинга полей
     */
    @PostMapping("/mappings")
    @ResponseBody
    public ResponseEntity<?> createMapping(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam Long clientId,
            @RequestParam String entityType,
            @RequestParam Map<String, String> fieldMapping,
            @RequestParam(defaultValue = "false") boolean composite,
            @RequestParam(required = false) List<String> relatedEntities) {
        try {
            log.debug("Запрос на создание маппинга полей: {}, клиент: {}, тип: {}",
                    name, clientId, entityType);

            // Проверяем существование клиента
            var clientOpt = clientService.getClientById(clientId);
            if (clientOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Клиент не найден: " + clientId));
            }

            // Фильтруем параметры запроса, оставляя только маппинг полей
            Map<String, String> actualMapping = new HashMap<>();
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                if (entry.getKey().startsWith("mapping.")) {
                    String sourceField = entry.getKey().substring("mapping.".length());
                    actualMapping.put(sourceField, entry.getValue());
                }
            }

            // Создаем маппинг
            Long mappingId = fieldMappingService.createMapping(
                    name, description, clientId, entityType, actualMapping, composite, relatedEntities);

            return ResponseEntity.ok(Map.of("id", mappingId, "message", "Маппинг успешно создан"));
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга полей: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}