package my.java.service.export.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.export.ExportTemplate;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.util.PathResolver;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExcelExportStrategy implements ExportStrategy {

    private final JdbcTemplate jdbcTemplate;
    private final PathResolver pathResolver;
    private final ValueTransformerFactory transformerFactory;

    private static final String[] SUPPORTED_FORMATS = {"xlsx", "xls"};

    @Override
    public String getStrategyId() {
        return "excel-export";
    }

    @Override
    public boolean isApplicable(String entityType, Map<String, String> parameters) {
        return true;
    }

    @Override
    public String[] getSupportedFormats() {
        return SUPPORTED_FORMATS;
    }

    @Override
    public Path executeExport(Client client, ExportTemplate template, FileOperation operation) {
        log.info("Запуск экспорта Excel для клиента {}, шаблон: {}", client.getId(), template.getName());

        try {
            // Обновление статуса операции
            operation.markAsProcessing();

            // Создание временного файла
            Path tempFilePath = Files.createTempFile("export_", "." + template.getFormat());

            // Получение полей для экспорта
            Map<String, String> fieldMapping = template.getFieldMapping();
            String entityType = template.getEntityType();

            String tableName = determineTableName(entityType);
            List<String> selectedFields = new ArrayList<>(fieldMapping.keySet());

            // Построение SQL запроса
            String sql = buildSqlQuery(tableName, selectedFields, client.getId(), template.getFilterCondition());

            // Выполнение запроса
            List<Map<String, Object>> resultData = jdbcTemplate.queryForList(sql);

            // Обновление информации о количестве записей
            operation.setTotalRecords(resultData.size());

            // Создание Excel-файла
            writeToExcel(tempFilePath, resultData, fieldMapping);

            // Перемещение файла в постоянное хранилище
            String filename = "export_" + client.getId() + "_" + System.currentTimeMillis() + "." + template.getFormat();
            Path exportFilePath = pathResolver.moveFromTempToUpload(
                    tempFilePath, "export_" + client.getId());

            // Обновление информации об операции
            operation.setResultFilePath(exportFilePath.toString());
            operation.setFileName(filename);
            operation.markAsCompleted(resultData.size());

            log.info("Успешно выполнен экспорт Excel, записей: {}, файл: {}",
                    resultData.size(), exportFilePath);

            return exportFilePath;

        } catch (Exception e) {
            log.error("Ошибка при экспорте в Excel: {}", e.getMessage(), e);
            operation.markAsFailed("Ошибка при экспорте: " + e.getMessage());
            throw new RuntimeException("Ошибка при экспорте в Excel", e);
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
     * Записывает данные в Excel файл
     */
    private void writeToExcel(Path filePath, List<Map<String, Object>> data,
                              Map<String, String> fieldMapping) throws Exception {

        // Определение заголовков
        List<String> sourceFields = new ArrayList<>(fieldMapping.keySet());
        List<String> targetFields = sourceFields.stream()
                .map(field -> fieldMapping.getOrDefault(field, field))
                .collect(Collectors.toList());

        // Создание рабочей книги Excel
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Данные");

            // Создание стилей для заголовков
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Запись заголовков
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < targetFields.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(targetFields.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000); // Устанавливаем ширину колонки
            }

            // Запись данных
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, Object> rowData = data.get(i);

                for (int j = 0; j < sourceFields.size(); j++) {
                    Cell cell = row.createCell(j);
                    Object value = rowData.get(sourceFields.get(j));
                    setCellValue(cell, value);
                }
            }

            // Автоматическая настройка ширины колонок
            for (int i = 0; i < targetFields.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Запись в файл
            try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                workbook.write(outputStream);
            }
        }
    }

    /**
     * Устанавливает значение ячейки в зависимости от типа данных
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }

        if (value instanceof Number) {
            if (value instanceof Integer || value instanceof Long) {
                cell.setCellValue(((Number) value).longValue());
            } else {
                cell.setCellValue(((Number) value).doubleValue());
            }
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            CellStyle dateStyle = cell.getSheet().getWorkbook().createCellStyle();
            CreationHelper createHelper = cell.getSheet().getWorkbook().getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd.mm.yyyy hh:mm:ss"));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(transformerFactory.toString(value, null));
        }
    }
}