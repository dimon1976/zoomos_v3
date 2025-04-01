package my.java.service.file.mapping;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Region;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Реализация сервиса маппинга полей.
 */
@Service
@Slf4j
@AllArgsConstructor
public class FieldMappingServiceImpl implements FieldMappingService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, String> getMappingById(Long mappingId) {
        if (mappingId == null) {
            return Collections.emptyMap();
        }

        try {
            // Получаем основную информацию о маппинге
            String mappingSql = "SELECT id, name, description, client_id, entity_type FROM field_mappings WHERE id = ? AND is_active = true";
            Map<String, Object> mapping = jdbcTemplate.queryForMap(mappingSql, mappingId);

            // Получаем детали маппинга
            String detailsSql = "SELECT source_field, target_field FROM field_mapping_details WHERE field_mapping_id = ? ORDER BY order_index";
            List<Map<String, Object>> details = jdbcTemplate.queryForList(detailsSql, mappingId);

            // Преобразуем детали в карту маппинга
            Map<String, String> fieldMapping = new HashMap<>();
            for (Map<String, Object> detail : details) {
                String sourceField = (String) detail.get("source_field");
                String targetField = (String) detail.get("target_field");
                fieldMapping.put(sourceField, targetField);
            }

            return fieldMapping;
        } catch (Exception e) {
            log.error("Ошибка при получении маппинга полей по ID {}: {}", mappingId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public List<Map<String, Object>> getAvailableMappingsForClient(Long clientId, String entityType) {
        String sql = """
                SELECT 
                    m.id, m.name, m.description, m.client_id, m.entity_type, 
                    m.created_at, m.updated_at,
                    COUNT(d.id) as fields_count
                FROM 
                    field_mappings m
                LEFT JOIN 
                    field_mapping_details d ON m.id = d.field_mapping_id
                WHERE 
                    m.is_active = true
                    AND (m.client_id = ? OR m.client_id IS NULL)
                    AND m.entity_type = ?
                GROUP BY 
                    m.id, m.name, m.description, m.client_id, m.entity_type, m.created_at, m.updated_at
                ORDER BY 
                    m.client_id NULLS LAST, m.name
                """;

        try {
            return jdbcTemplate.queryForList(sql, clientId, entityType);
        } catch (Exception e) {
            log.error("Ошибка при получении доступных маппингов для клиента {}, тип сущности {}: {}",
                    clientId, entityType, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public Long createMapping(String name, String description, Long clientId, String entityType, Map<String, String> fieldMapping) {
        try {
            // Проверяем, что имя маппинга уникально для клиента и типа сущности
            String checkSql = "SELECT COUNT(*) FROM field_mappings WHERE name = ? AND client_id = ? AND entity_type = ?";
            int count = jdbcTemplate.queryForObject(checkSql, Integer.class, name, clientId, entityType);
            if (count > 0) {
                throw new FileOperationException("Маппинг с именем '" + name + "' уже существует для этого клиента и типа сущности");
            }

            // Вставляем запись о маппинге
            String insertMappingSql = """
                    INSERT INTO field_mappings (name, description, client_id, entity_type, created_at, updated_at, is_active) 
                    VALUES (?, ?, ?, ?, now(), now(), true) RETURNING id
                    """;
            Long mappingId = jdbcTemplate.queryForObject(insertMappingSql, Long.class, name, description, clientId, entityType);

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

            log.info("Создан новый маппинг полей: id={}, name={}, entityType={}, полей={}",
                    mappingId, name, entityType, fieldMapping.size());
            return mappingId;
        } catch (FileOperationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга полей: {}", e.getMessage());
            throw new FileOperationException("Не удалось создать маппинг полей: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean updateMapping(Long mappingId, String name, String description, Map<String, String> fieldMapping) {
        try {
            // Проверяем существование маппинга
            String checkSql = "SELECT client_id, entity_type FROM field_mappings WHERE id = ? AND is_active = true";
            try {
                Map<String, Object> mapping = jdbcTemplate.queryForMap(checkSql, mappingId);
                Long clientId = (Long) mapping.get("client_id");
                String entityType = (String) mapping.get("entity_type");

                // Проверяем уникальность имени
                String uniqueSql = "SELECT COUNT(*) FROM field_mappings WHERE name = ? AND client_id = ? AND entity_type = ? AND id != ?";
                int count = jdbcTemplate.queryForObject(uniqueSql, Integer.class, name, clientId, entityType, mappingId);
                if (count > 0) {
                    throw new FileOperationException("Маппинг с именем '" + name + "' уже существует для этого клиента и типа сущности");
                }
            } catch (Exception e) {
                if (!(e instanceof FileOperationException)) {
                    log.error("Маппинг с ID {} не найден", mappingId);
                    return false;
                }
                throw e;
            }

            // Обновляем основную запись
            String updateMappingSql = "UPDATE field_mappings SET name = ?, description = ?, updated_at = now() WHERE id = ?";
            int updated = jdbcTemplate.update(updateMappingSql, name, description, mappingId);
            if (updated == 0) {
                return false;
            }

            // Удаляем старые детали
            String deleteDetailsSql = "DELETE FROM field_mapping_details WHERE field_mapping_id = ?";
            jdbcTemplate.update(deleteDetailsSql, mappingId);

            // Добавляем новые детали
            String insertDetailSql = """
                    INSERT INTO field_mapping_details 
                    (field_mapping_id, source_field, target_field, required, order_index) 
                    VALUES (?, ?, ?, ?, ?)
                    """;
            int order = 0;
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                jdbcTemplate.update(insertDetailSql, mappingId, entry.getKey(), entry.getValue(), false, order++);
            }

            log.info("Обновлен маппинг полей: id={}, name={}, полей={}", mappingId, name, fieldMapping.size());
            return true;
        } catch (FileOperationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при обновлении маппинга полей: {}", e.getMessage());
            throw new FileOperationException("Не удалось обновить маппинг полей: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean deleteMapping(Long mappingId) {
        try {
            // Мягкое удаление - просто помечаем как неактивный
            String sql = "UPDATE field_mappings SET is_active = false, updated_at = now() WHERE id = ?";
            int updated = jdbcTemplate.update(sql, mappingId);

            if (updated > 0) {
                log.info("Маппинг полей с ID {} помечен как неактивный", mappingId);
                return true;
            } else {
                log.warn("Маппинг полей с ID {} не найден при попытке удаления", mappingId);
                return false;
            }
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга полей с ID {}: {}", mappingId, e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, String> suggestMapping(List<String> headers, String entityType) {
        // Получаем экземпляр сущности для доступа к ее маппингу полей
        ImportableEntity entity = createEntityInstance(entityType);
        if (entity == null) {
            log.warn("Не удалось создать экземпляр сущности типа {}", entityType);
            return Collections.emptyMap();
        }

        Map<String, String> entityFieldMapping = entity.getFieldMappings();
        if (entityFieldMapping == null || entityFieldMapping.isEmpty()) {
            log.warn("Сущность типа {} не предоставляет маппинг полей", entityType);
            return Collections.emptyMap();
        }

        Map<String, String> suggestedMapping = new HashMap<>();

        // Для каждого заголовка из файла ищем совпадение в маппинге полей сущности
        for (String header : headers) {
            // Точное совпадение
            if (entityFieldMapping.containsKey(header)) {
                suggestedMapping.put(header, entityFieldMapping.get(header));
                continue;
            }

            // Совпадение без учета регистра
            String matchedKey = findCaseInsensitiveMatch(header, entityFieldMapping.keySet());
            if (matchedKey != null) {
                suggestedMapping.put(header, entityFieldMapping.get(matchedKey));
                continue;
            }

            // Поиск по похожести
            matchedKey = findSimilarMatch(header, entityFieldMapping.keySet());
            if (matchedKey != null) {
                suggestedMapping.put(header, entityFieldMapping.get(matchedKey));
            }
        }

        return suggestedMapping;
    }

    @Override
    public Map<String, Object> getEntityFieldsMetadata(String entityType) {
        ImportableEntity entity = createEntityInstance(entityType);
        if (entity == null) {
            log.warn("Не удалось создать экземпляр сущности типа {}", entityType);
            return Collections.emptyMap();
        }

        Map<String, String> fieldMappings = entity.getFieldMappings();
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("entityType", entityType);
        metadata.put("fieldMappings", fieldMappings);

        // Дополнительные метаданные могут быть добавлены при необходимости

        return metadata;
    }

    /**
     * Ищет совпадение строки без учета регистра в наборе ключей.
     *
     * @param target целевая строка
     * @param keys   набор ключей
     * @return найденный ключ или null
     */
    private String findCaseInsensitiveMatch(String target, Set<String> keys) {
        for (String key : keys) {
            if (key.equalsIgnoreCase(target)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Ищет похожий ключ в наборе ключей.
     *
     * @param target целевая строка
     * @param keys   набор ключей
     * @return найденный ключ или null
     */
    private String findSimilarMatch(String target, Set<String> keys) {
        String targetNormalized = normalizeString(target);

        for (String key : keys) {
            String keyNormalized = normalizeString(key);

            // Проверяем, содержит ли один нормализованный ключ другой
            if (keyNormalized.contains(targetNormalized) || targetNormalized.contains(keyNormalized)) {
                return key;
            }
        }

        return null;
    }

    /**
     * Нормализует строку для поиска совпадений.
     *
     * @param input входная строка
     * @return нормализованная строка
     */
    private String normalizeString(String input) {
        return input.toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^a-zA-Zа-яА-Я0-9]", "");
    }

    /**
     * Создает экземпляр сущности по типу.
     *
     * @param entityType тип сущности
     * @return экземпляр сущности или null
     */
    private ImportableEntity createEntityInstance(String entityType) {
        try {
            switch (entityType.toLowerCase()) {
                case "product":
                    return new my.java.model.entity.Product();
                case "regiondata":
                    return new Region();
                case "competitordata":
                    return new Competitor();
                default:
                    log.warn("Неизвестный тип сущности: {}", entityType);
                    return null;
            }
        } catch (Exception e) {
            log.error("Ошибка при создании экземпляра сущности типа {}: {}", entityType, e.getMessage());
            return null;
        }
    }
}