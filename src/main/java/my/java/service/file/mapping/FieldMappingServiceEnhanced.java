// src/main/java/my/java/service/file/mapping/FieldMappingServiceEnhanced.java
package my.java.service.file.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.service.file.metadata.EntityMetadata;
import my.java.service.file.metadata.EntityRegistry;
import my.java.service.file.options.FileReadingOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Расширенный сервис маппинга полей для работы с составными сущностями
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FieldMappingServiceEnhanced {

    private final JdbcTemplate jdbcTemplate;
    private final EntityRegistry entityRegistry;

    /**
     * Получает карту маппинга полей по его ID
     */
    public Map<String, String> getMappingById(Long mappingId) {
        if (mappingId == null) {
            return Collections.emptyMap();
        }

        try {
            // Получаем информацию о маппинге
            String mappingSql = "SELECT id FROM field_mappings WHERE id = ? AND is_active = true";
            jdbcTemplate.queryForObject(mappingSql, Long.class, mappingId);

            // Получаем детали маппинга
            String detailsSql = "SELECT source_field, target_field FROM field_mapping_details WHERE field_mapping_id = ? ORDER BY order_index";
            List<Map<String, Object>> details = jdbcTemplate.queryForList(detailsSql, mappingId);

            // Преобразуем детали в карту маппинга
            Map<String, String> fieldMapping = new HashMap<>();
            for (Map<String, Object> detail : details) {
                fieldMapping.put(
                        (String) detail.get("source_field"),
                        (String) detail.get("target_field")
                );
            }

            return fieldMapping;
        } catch (Exception e) {
            log.error("Ошибка при получении маппинга полей по ID {}: {}", mappingId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Получает список всех доступных маппингов для клиента и типа сущности
     */
    public List<Map<String, Object>> getAvailableMappingsForClient(Long clientId, String entityType) {
        String sql = """
                SELECT 
                    m.id, m.name, m.description, m.client_id, m.entity_type, m.composite,
                    m.created_at, m.updated_at,
                    COUNT(d.id) as fields_count
                FROM 
                    field_mappings m
                LEFT JOIN 
                    field_mapping_details d ON m.id = d.field_mapping_id
                WHERE 
                    m.is_active = true
                    AND (m.client_id = ? OR m.client_id IS NULL)
                    AND (m.entity_type = ? OR (m.composite = true AND ? = ANY(string_to_array(m.related_entities, ','))))
                GROUP BY 
                    m.id, m.name, m.description, m.client_id, m.entity_type, m.composite, m.created_at, m.updated_at
                ORDER BY 
                    m.client_id NULLS LAST, m.name
                """;

        try {
            return jdbcTemplate.queryForList(sql, clientId, entityType, entityType);
        } catch (Exception e) {
            log.error("Ошибка при получении доступных маппингов: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Создает новый маппинг полей с поддержкой составных сущностей
     */
    @Transactional
    public Long createMapping(String name, String description, Long clientId, String entityType,
                              Map<String, String> fieldMapping, boolean isComposite, List<String> relatedEntities) {
        try {
            // Проверяем уникальность имени маппинга
            String checkSql = "SELECT COUNT(*) FROM field_mappings WHERE name = ? AND client_id = ? AND entity_type = ?";
            int count = jdbcTemplate.queryForObject(checkSql, Integer.class, name, clientId, entityType);
            if (count > 0) {
                throw new FileOperationException("Маппинг с именем '" + name + "' уже существует");
            }

            // Подготавливаем строку со связанными сущностями
            String relatedEntitiesStr = relatedEntities != null ? String.join(",", relatedEntities) : null;

            // Вставляем запись о маппинге
            String insertMappingSql = """
                    INSERT INTO field_mappings 
                    (name, description, client_id, entity_type, composite, related_entities, created_at, updated_at, is_active) 
                    VALUES (?, ?, ?, ?, ?, ?, now(), now(), true) RETURNING id
                    """;
            Long mappingId = jdbcTemplate.queryForObject(insertMappingSql, Long.class,
                    name, description, clientId, entityType, isComposite, relatedEntitiesStr);

            // Вставляем детали маппинга
            String insertDetailSql = """
                    INSERT INTO field_mapping_details 
                    (field_mapping_id, source_field, target_field, required, order_index) 
                    VALUES (?, ?, ?, ?, ?)
                    """;
            int order = 0;
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                jdbcTemplate.update(insertDetailSql, mappingId, entry.getKey(), entry.getValue(), false, order++);
            }

            log.info("Создан новый маппинг полей: id={}, name={}, полей={}", mappingId, name, fieldMapping.size());
            return mappingId;
        } catch (FileOperationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга полей: {}", e.getMessage());
            throw new FileOperationException("Не удалось создать маппинг полей: " + e.getMessage());
        }
    }

    /**
     * Сопоставляет заголовки из файла с полями сущности с учетом префиксов
     */
    public Map<String, String> suggestMapping(List<String> headers, String entityType) {
        // Проверяем метаданные сущности
        EntityMetadata metadata = entityRegistry.getEntityMetadata(entityType);
        if (metadata == null) {
            log.warn("Не найдены метаданные для типа сущности: {}", entityType);
            return Collections.emptyMap();
        }

        Map<String, String> suggestedMapping = new HashMap<>();
        Map<String, EntityMetadata.FieldMetadata> fieldsToMap;

        // Получаем поля в зависимости от типа сущности
        fieldsToMap = metadata.isMainEntity()
                ? entityRegistry.getCompositeEntityFields(entityType)
                : entityRegistry.getPrefixedFieldsForEntity(entityType);

        // Создаем обратную карту для поиска по отображаемым именам
        Map<String, String> displayToField = new HashMap<>();
        fieldsToMap.forEach((fieldName, fieldMetadata) -> {
            displayToField.put(fieldMetadata.getDisplayName().toLowerCase(), fieldName);
        });

        // Для каждого заголовка ищем совпадение
        for (String header : headers) {
            String lowerHeader = header.toLowerCase();

            // Прямое совпадение
            if (displayToField.containsKey(lowerHeader)) {
                suggestedMapping.put(header, displayToField.get(lowerHeader));
                continue;
            }

            // Приблизительное совпадение
            String bestMatch = findBestMatch(lowerHeader, displayToField.keySet());
            if (bestMatch != null) {
                suggestedMapping.put(header, displayToField.get(bestMatch));
            }
        }

        return suggestedMapping;
    }

    /**
     * Получает метаданные о полях для составной сущности с использованием FileReadingOptions
     */
    public Map<String, Object> getCompositeEntityFieldsMetadataWithOptions(
            String mainEntityType,
            FileReadingOptions options) {

        Map<String, Object> result = new HashMap<>();

        // Получаем метаданные основной сущности
        EntityMetadata mainMetadata = entityRegistry.getEntityMetadata(mainEntityType);
        if (mainMetadata == null) {
            log.warn("Не найдены метаданные для типа сущности: {}", mainEntityType);
            return result;
        }

        // Получаем все поля
        Map<String, EntityMetadata.FieldMetadata> allFields =
                entityRegistry.getCompositeEntityFieldsWithOptions(mainEntityType, options);

        // Группируем поля по типам сущностей
        Map<String, List<Map<String, Object>>> fieldsByEntity = new HashMap<>();

        allFields.forEach((fieldName, fieldMetadata) -> {
            String[] parts = fieldName.split("\\.");
            String entityPrefix = parts[0];

            Map<String, Object> fieldInfo = Map.of(
                    "name", fieldName,
                    "displayName", fieldMetadata.getDisplayName(),
                    "type", fieldMetadata.getType().getSimpleName(),
                    "required", fieldMetadata.isRequired(),
                    "exportable", fieldMetadata.isExportable()
            );

            fieldsByEntity.computeIfAbsent(entityPrefix, k -> new ArrayList<>()).add(fieldInfo);
        });

        // Добавляем информацию о сущностях
        List<Map<String, Object>> entities = new ArrayList<>();

        // Основная сущность
        Map<String, Object> mainEntityInfo = Map.of(
                "type", mainEntityType,
                "displayName", mainMetadata.getDisplayName(),
                "isMain", true,
                "fields", fieldsByEntity.getOrDefault(mainEntityType, Collections.emptyList())
        );
        entities.add(mainEntityInfo);

        // Получаем связанные сущности из опций
        String relatedEntitiesStr = options.getAdditionalParam("relatedEntities", "");
        List<String> relationTypes = relatedEntitiesStr.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(relatedEntitiesStr.split(","));

        // Связанные сущности
        List<EntityMetadata> relatedEntities = entityRegistry.getRelatedEntities(mainEntityType);
        for (EntityMetadata relatedMetadata : relatedEntities) {
            // Проверяем наличие в списке, если он указан
            if (!relationTypes.isEmpty() && !relationTypes.contains(relatedMetadata.getEntityType())) {
                continue;
            }

            Map<String, Object> relatedEntityInfo = Map.of(
                    "type", relatedMetadata.getEntityType(),
                    "displayName", relatedMetadata.getDisplayName(),
                    "isMain", false,
                    "fields", fieldsByEntity.getOrDefault(relatedMetadata.getEntityType(), Collections.emptyList())
            );
            entities.add(relatedEntityInfo);
        }

        result.put("mainEntityType", mainEntityType);
        result.put("entities", entities);
        result.put("allFields", allFields.values().stream()
                .map(field -> Map.of(
                        "name", field.getName(),
                        "displayName", field.getDisplayName(),
                        "type", field.getType().getSimpleName()
                ))
                .collect(Collectors.toList()));

        return result;
    }

    /**
     * Находит лучшее совпадение для заголовка
     */
    private String findBestMatch(String header, Set<String> availableHeaders) {
        String bestMatch = null;
        int maxScore = 0;

        for (String available : availableHeaders) {
            int score = calculateSimilarityScore(header, available);
            if (score > maxScore) {
                maxScore = score;
                bestMatch = available;
            }
        }

        // Минимальный порог схожести
        return maxScore > 3 ? bestMatch : null;
    }

    /**
     * Рассчитывает оценку схожести двух строк
     */
    private int calculateSimilarityScore(String str1, String str2) {
        // Содержит ли одна строка другую
        if (str1.contains(str2) || str2.contains(str1)) {
            return 10;
        }

        // Подсчет общих слов
        String[] words1 = str1.split("\\s+");
        String[] words2 = str2.split("\\s+");

        int commonWords = 0;
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equalsIgnoreCase(word2) && word1.length() > 2) {
                    commonWords++;
                }
            }
        }

        return commonWords * 2;
    }
}