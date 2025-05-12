// src/main/java/my/java/service/entity/EntityDataService.java
package my.java.service.entity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EntityDataService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Получает данные сущности для экспорта
     */
    public List<Map<String, String>> getEntityDataForExport(
            String entityType, List<String> fields, Map<String, Object> filterParams, Long clientId) {

        // Группируем поля по сущностям
        Map<String, List<String>> fieldsByEntity = new HashMap<>();

        for (String field : fields) {
            String entity = entityType; // По умолчанию основная сущность
            String fieldName = field;

            // Извлекаем префикс сущности, если есть
            int dotIndex = field.indexOf('.');
            if (dotIndex > 0) {
                entity = field.substring(0, dotIndex);
                fieldName = field.substring(dotIndex + 1);
            }

            fieldsByEntity.computeIfAbsent(entity, k -> new ArrayList<>()).add(fieldName);
        }

        // Если выбраны поля только из основной сущности, используем простой запрос
        if (fieldsByEntity.size() == 1 && fieldsByEntity.containsKey(entityType)) {
            return getDataFromSingleEntity(entityType, fieldsByEntity.get(entityType), filterParams, clientId);
        }

        // Иначе используем запрос с JOIN для связанных сущностей
        return getDataFromMultipleEntities(entityType, fieldsByEntity, filterParams, clientId);
    }

    /**
     * Получает данные из нескольких связанных сущностей
     */
    private List<Map<String, String>> getDataFromMultipleEntities(
            String mainEntityType, Map<String, List<String>> fieldsByEntity,
            Map<String, Object> filterParams, Long clientId) {

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");

            // Формируем список полей для выборки
            List<String> selectClauses = new ArrayList<>();
            Map<String, String> fieldAliasMap = new HashMap<>(); // для сопоставления алиасов с полями

            // Добавляем ID основной сущности всегда для правильного соединения
            selectClauses.add(getTableAlias(mainEntityType) + ".id AS main_id");

            // Добавляем поля основной сущности
            if (fieldsByEntity.containsKey(mainEntityType)) {
                for (String fieldName : fieldsByEntity.get(mainEntityType)) {
                    String dbField = convertFieldNameToDbColumn(fieldName);
                    String alias = mainEntityType + "_" + dbField;
                    selectClauses.add(getTableAlias(mainEntityType) + "." + dbField + " AS " + alias);
                    fieldAliasMap.put(alias, mainEntityType + "." + fieldName);
                }
            }

            // Добавляем поля связанных сущностей
            for (Map.Entry<String, List<String>> entry : fieldsByEntity.entrySet()) {
                if (!entry.getKey().equals(mainEntityType)) {
                    for (String fieldName : entry.getValue()) {
                        String dbField = convertFieldNameToDbColumn(fieldName);
                        String alias = entry.getKey() + "_" + dbField;
                        selectClauses.add(getTableAlias(entry.getKey()) + "." + dbField + " AS " + alias);
                        fieldAliasMap.put(alias, entry.getKey() + "." + fieldName);
                    }
                }
            }

            sql.append(String.join(", ", selectClauses));

            // Основная таблица
            sql.append(" FROM ").append(getTableName(mainEntityType))
                    .append(" AS ").append(getTableAlias(mainEntityType));

            // JOIN для связанных сущностей
            for (String entity : fieldsByEntity.keySet()) {
                if (!entity.equals(mainEntityType)) {
                    String joinField = "product_id"; // Поле для соединения, обычно это product_id в связанных таблицах

                    sql.append(" LEFT JOIN ")
                            .append(getTableName(entity))
                            .append(" AS ").append(getTableAlias(entity))
                            .append(" ON ").append(getTableAlias(mainEntityType)).append(".id = ")
                            .append(getTableAlias(entity)).append(".").append(joinField);
                }
            }

            // WHERE условия
            sql.append(" WHERE ").append(getTableAlias(mainEntityType)).append(".client_id = ?");

            List<Object> params = new ArrayList<>();
            params.add(clientId);

            // Добавляем дополнительные фильтры
            if (filterParams != null && !filterParams.isEmpty()) {
                addJoinFilters(sql, filterParams, params, mainEntityType, fieldsByEntity.keySet());
            }

            // Логирование SQL-запроса для отладки
            log.debug("SQL запрос для составных сущностей: {}", sql.toString());
            log.debug("Параметры запроса: {}", params);

            // Выполняем запрос
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql.toString(), params.toArray());

            // Преобразуем результаты в нужный формат используя сопоставление алиасов
            List<Map<String, String>> convertedResults = new ArrayList<>();

            for (Map<String, Object> row : results) {
                Map<String, String> convertedRow = new HashMap<>();

                for (Map.Entry<String, String> mapping : fieldAliasMap.entrySet()) {
                    String alias = mapping.getKey();
                    String originalField = mapping.getValue();

                    Object value = row.get(alias);
                    convertedRow.put(originalField, value != null ? value.toString() : "");
                }

                convertedResults.add(convertedRow);
            }

            return convertedResults;

        } catch (Exception e) {
            log.error("Ошибка при выполнении SQL запроса для составных сущностей: {}", e.getMessage(), e);
            // Возвращаем пустой список если произошла ошибка, чтобы не прерывать весь процесс
            return new ArrayList<>();
        }
    }


    /**
     * Добавляет фильтры для запроса с JOIN
     */
    private void addJoinFilters(StringBuilder sql, Map<String, Object> filterParams,
                                List<Object> params, String mainEntityType, Set<String> entities) {
        for (Map.Entry<String, Object> entry : filterParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                continue;
            }

            // Определяем, к какой сущности относится поле
            String entityPrefix = mainEntityType;
            String fieldName = key;

            for (String entity : entities) {
                if (key.startsWith(entity + ".")) {
                    entityPrefix = entity;
                    fieldName = key.substring(entity.length() + 1);
                    break;
                }
            }

            String tableAlias = getTableAlias(entityPrefix);
            String dbField = convertFieldNameToDbColumn(fieldName);

            switch (key) {
                case "name":
                    sql.append(" AND ").append(tableAlias).append(".product_name ILIKE ?");
                    params.add("%" + value + "%");
                    break;
                case "fromDate":
                    sql.append(" AND ").append(tableAlias).append(".created_at >= ?");
                    params.add(value);
                    break;
                case "toDate":
                    sql.append(" AND ").append(tableAlias).append(".created_at <= ?");
                    params.add(value);
                    break;
                case "minPrice":
                    sql.append(" AND CAST(").append(tableAlias).append(".product_price AS numeric) >= ?");
                    params.add(value);
                    break;
                case "maxPrice":
                    sql.append(" AND CAST(").append(tableAlias).append(".product_price AS numeric) <= ?");
                    params.add(value);
                    break;
                default:
                    // Для других фильтров
                    sql.append(" AND ").append(tableAlias).append(".").append(dbField).append(" = ?");
                    params.add(value);
                    break;
            }
        }
    }


    /**
     * Получает псевдоним таблицы для SQL запроса
     */
    private String getTableAlias(String entityType) {
        switch (entityType.toLowerCase()) {
            case "product":
                return "p";
            case "competitor":
                return "c";
            case "region":
                return "r";
            default:
                return entityType.substring(0, 1).toLowerCase();
        }
    }

    /**
     * Получает данные из одной сущности
     */
    private List<Map<String, String>> getDataFromSingleEntity(
            String entityType, List<String> fieldNames, Map<String, Object> filterParams, Long clientId) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");

        // Формируем список полей для выборки
        List<String> dbFields = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String dbField = convertFieldNameToDbColumn(fieldName);
            dbFields.add(dbField);
        }

        sql.append(String.join(", ", dbFields));

        // Добавляем условия выборки
        sql.append(" FROM ").append(getTableName(entityType));
        sql.append(" WHERE client_id = ?");

        List<Object> params = new ArrayList<>();
        params.add(clientId);

        // Добавляем дополнительные фильтры
        if (filterParams != null && !filterParams.isEmpty()) {
            addFilters(sql, filterParams, params);
        }

        // Выполняем запрос
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        // Преобразуем результаты в нужный формат
        return convertResults(results, fieldNames.stream()
                .map(name -> entityType + "." + name)
                .collect(Collectors.toList()));
    }

    /**
     * Конвертирует имя поля в имя столбца БД
     */
    private String convertFieldNameToDbColumn(String fieldName) {
        // Удаляем префикс сущности, если есть
        int dotIndex = fieldName.indexOf('.');
        if (dotIndex > 0) {
            fieldName = fieldName.substring(dotIndex + 1);
        }

        // Преобразуем camelCase в snake_case
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Получает имя таблицы по типу сущности
     */
    private String getTableName(String entityType) {
        switch (entityType.toLowerCase()) {
            case "product":
                return "products";
            case "competitor":
                return "competitor_data";
            case "region":
                return "region_data";
            default:
                return entityType.toLowerCase() + "s";
        }
    }

    /**
     * Добавляет фильтры к SQL-запросу
     */
    private void addFilters(StringBuilder sql, Map<String, Object> filterParams, List<Object> params) {
        for (Map.Entry<String, Object> entry : filterParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                continue;
            }

            // Добавляем фильтр по операциям импорта
            if (key.equals("importOperationIds")) {
                if (value instanceof List) {
                    List<?> operationIds = (List<?>) value;
                    if (!operationIds.isEmpty()) {
                        sql.append(" AND import_operation_id IN (");
                        for (int i = 0; i < operationIds.size(); i++) {
                            sql.append(i > 0 ? ", ?" : "?");
                            params.add(operationIds.get(i));
                        }
                        sql.append(")");
                    }
                } else if (value instanceof String) {
                    // Если передана строка с разделителями
                    String[] ids = ((String) value).split(",");
                    if (ids.length > 0) {
                        sql.append(" AND import_operation_id IN (");
                        for (int i = 0; i < ids.length; i++) {
                            sql.append(i > 0 ? ", ?" : "?");
                            try {
                                params.add(Long.parseLong(ids[i].trim()));
                            } catch (NumberFormatException e) {
                                log.warn("Некорректный формат ID операции: {}", ids[i]);
                            }
                        }
                        sql.append(")");
                    }
                } else {
                    // Если передано одно значение
                    sql.append(" AND import_operation_id = ?");
                    params.add(value);
                }
            } else {
                // Существующая логика для других фильтров...
                switch (key) {
                    case "name":
                        sql.append(" AND product_name ILIKE ?");
                        params.add("%" + value + "%");
                        break;
                    case "fromDate":
                        sql.append(" AND created_at >= ?");
                        params.add(value);
                        break;
                    case "toDate":
                        sql.append(" AND created_at <= ?");
                        params.add(value);
                        break;
                    case "minPrice":
                        sql.append(" AND CAST(product_price AS numeric) >= ?");
                        params.add(value);
                        break;
                    case "maxPrice":
                        sql.append(" AND CAST(product_price AS numeric) <= ?");
                        params.add(value);
                        break;
                    default:
                        // Для других фильтров добавляем прямое сравнение
                        String column = convertFieldNameToDbColumn(key);
                        sql.append(" AND ").append(column).append(" = ?");
                        params.add(value);
                        break;
                }
            }
        }
    }


    /**
     * Преобразует результаты запроса в нужный формат
     */
    private List<Map<String, String>> convertResults(List<Map<String, Object>> results, List<String> fields) {
        List<Map<String, String>> convertedResults = new ArrayList<>();

        for (Map<String, Object> row : results) {
            Map<String, String> convertedRow = new HashMap<>();

            for (String field : fields) {
                // Для полей с префиксом (например, "product.id")
                String dbField;
                if (field.contains(".")) {
                    String[] parts = field.split("\\.", 2);
                    dbField = getTableAlias(parts[0]) + "." + convertFieldNameToDbColumn(parts[1]);
                } else {
                    dbField = convertFieldNameToDbColumn(field);
                }

                // Получаем значение из результата запроса
                Object value = null;
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    // Проверяем полное соответствие или соответствие без алиаса
                    if (entry.getKey().equals(dbField) ||
                            entry.getKey().equals(dbField.substring(dbField.indexOf('.') + 1))) {
                        value = entry.getValue();
                        break;
                    }
                }

                // Добавляем значение в результат
                convertedRow.put(field, value != null ? value.toString() : "");
            }

            convertedResults.add(convertedRow);
        }

        return convertedResults;
    }
}