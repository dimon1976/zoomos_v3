package my.java.service.export.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.export.ExportTemplate;
import my.java.service.file.options.FileReadingOptions;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.util.PathResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class SimpleDataExportStrategy implements ExportStrategy {

    private final JdbcTemplate jdbcTemplate;
    private final PathResolver pathResolver;
    private final ValueTransformerFactory transformerFactory;

    private static final String[] SUPPORTED_FORMATS = {"csv", "txt"};

    @Override
    public String getStrategyId() {
        return "simple-data-export";
    }

    @Override
    public boolean isApplicable(String entityType, Map<String, String> parameters) {
        // Базовая стратегия подходит для всех типов сущностей
        return true;
    }

    @Override
    public String[] getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }

    @Override
    public Path executeExport(Client client, ExportTemplate template, FileOperation operation) {
        log.info("Запуск экспорта данных для клиента {}, шаблон: {}", client.getId(), template.getName());

        try {
            // Обновление статуса операции
            operation.markAsProcessing();

            // Создание временного файла
            String format = template.getFormat();
            String filename = "export_" + System.currentTimeMillis() + "." + format;
            Path tempFilePath = Files.createTempFile("export_", "." + format);

            // Получение полей для экспорта и создание SQL запроса
            Map<String, String> fieldMapping = template.getFieldMapping();
            String entityType = template.getEntityType();

            String tableName = determineTableName(entityType);
            List<String> selectedFields = new ArrayList<>(fieldMapping.keySet());

            // Базовое построение SQL запроса
            String sql = buildSqlQuery(tableName, selectedFields, client.getId(), template.getFilterCondition());

            // Выполнение запроса и запись результатов в файл
            List<Map<String, Object>> resultData = jdbcTemplate.queryForList(sql);

            // Обновление информации о количестве записей
            operation.setTotalRecords(resultData.size());

            // Запись данных в файл
            writeDataToFile(tempFilePath, resultData, fieldMapping, format);

            // Перемещение файла в постоянное хранилище
            Path exportFilePath = pathResolver.moveFromTempToUpload(
                    tempFilePath, "export_" + client.getId());

            // Обновление информации об операции
            operation.setResultFilePath(exportFilePath.toString());
            operation.setFileName(filename);
            operation.markAsCompleted(resultData.size());

            log.info("Успешно выполнен экспорт данных, записей: {}, файл: {}",
                    resultData.size(), exportFilePath);

            return exportFilePath;

        } catch (Exception e) {
            log.error("Ошибка при экспорте данных: {}", e.getMessage(), e);
            operation.markAsFailed("Ошибка при экспорте: " + e.getMessage());
            throw new RuntimeException("Ошибка при экспорте данных", e);
        }
    }

    /**
     * Определяет имя таблицы на основе типа сущности
     */
    private String determineTableName(String entityType) {
        switch (entityType.toLowerCase()) {
            case "product": return "products";
            case "region": return "region_data";
            case "competitor": return "competitor_data";
            default: throw new IllegalArgumentException("Неизвестный тип сущности: " + entityType);
        }
    }

    /**
     * Строит SQL запрос для экспорта данных
     */
    private String buildSqlQuery(String tableName, List<String> fields, Long clientId, String filterCondition) {
        String fieldsPart = fields.stream()
                .map(field -> field.contains(".") ? field : tableName + "." + field)
                .collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder("SELECT ").append(fieldsPart)
                .append(" FROM ").append(tableName)
                .append(" WHERE client_id = ").append(clientId);

        if (filterCondition != null && !filterCondition.trim().isEmpty()) {
            sql.append(" AND (").append(filterCondition).append(")");
        }

        return sql.toString();
    }

    /**
     * Записывает данные в файл в соответствии с форматом
     */
    private void writeDataToFile(Path filePath, List<Map<String, Object>> data,
                                 Map<String, String> fieldMapping, String format) throws Exception {

        // Определение заголовков
        List<String> sourceFields = new ArrayList<>(fieldMapping.keySet());
        List<String> targetFields = sourceFields.stream()
                .map(field -> fieldMapping.getOrDefault(field, field))
                .collect(Collectors.toList());

        // Запись в файл
        try (BufferedWriter writer = Files.newBufferedWriter(filePath,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {

            char delimiter = format.equals("csv") ? ',' : '\t';

            // Запись заголовков
            writer.write(String.join(String.valueOf(delimiter), targetFields));
            writer.newLine();

            // Запись данных
            for (Map<String, Object> row : data) {
                List<String> rowValues = new ArrayList<>();

                for (String field : sourceFields) {
                    Object value = row.get(field);
                    String formattedValue = formatValue(value, delimiter);
                    rowValues.add(formattedValue);
                }

                writer.write(String.join(String.valueOf(delimiter), rowValues));
                writer.newLine();
            }
        }
    }

    /**
     * Форматирует значение для записи в файл
     */
    private String formatValue(Object value, char delimiter) {
        if (value == null) {
            return "";
        }

        String strValue = transformerFactory.toString(value, null);

        // Экранирование специальных символов
        if (strValue.contains(String.valueOf(delimiter)) ||
                strValue.contains("\"") ||
                strValue.contains("\n")) {

            return "\"" + strValue.replace("\"", "\"\"") + "\"";
        }

        return strValue;
    }
}