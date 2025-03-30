package my.java.service.file.exporter.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.exporter.ExportConfig;
import my.java.service.file.exporter.tracker.ExportProgressTracker;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Процессор для экспорта данных в Excel
 * Путь: /java/my/java/service/file/exporter/processor/ExcelFileExportProcessor.java
 */
@Component
@Slf4j
public class ExcelFileExportProcessor<T extends ImportableEntity> extends AbstractFileExportProcessor<T> {

    @Override
    public void process(
            List<T> entities,
            ExportConfig config,
            OutputStream outputStream,
            ExportProgressTracker progressTracker,
            Long operationId) {

        if (entities == null || entities.isEmpty()) {
            log.warn("Пустой список сущностей для экспорта в Excel");
            return;
        }

        Class<T> entityClass = (Class<T>) entities.get(0).getClass();
        this.entityClass = entityClass; // Сохраняем класс сущности для использования в getHeaderNames

        try (Workbook workbook = new XSSFWorkbook()) {
            // Создаем лист для данных
            Sheet sheet = workbook.createSheet("Data");

            // Получение списка полей и заголовков
            List<String> fieldNames = getFieldNames(entityClass, config);
            List<String> headerNames = getHeaderNames(fieldNames, config);

            log.debug("Экспорт Excel: выбрано {} полей: {}", fieldNames.size(), fieldNames);
            log.debug("Экспорт Excel: заголовки: {}", headerNames);

            // Создаем стили для заголовка и ячеек данных
            CellStyle headerStyle = createHeaderStyle(workbook);
            Map<String, CellStyle> dataStyles = createDataStyles(workbook, fieldNames);

            // Текущая строка для записи
            AtomicInteger rowNum = new AtomicInteger();

            // Запись заголовка, если нужно
            if (config.isIncludeHeader()) {
                Row headerRow = sheet.createRow(rowNum.getAndIncrement());
                for (int i = 0; i < headerNames.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headerNames.get(i));
                    cell.setCellStyle(headerStyle);
                }
            }

            // Создаем структуру для хранения ширины столбцов
            final int[] columnWidths = new int[fieldNames.size()];

            // Обработка сущностей с обновлением прогресса
            processEntitiesWithProgress(entities, config, progressTracker, operationId, entity -> {
                Row row = sheet.createRow(rowNum.getAndIncrement());

                for (int i = 0; i < fieldNames.size(); i++) {
                    String fieldName = fieldNames.get(i);
                    Cell cell = row.createCell(i);

                    // Получаем значение и форматируем его
                    Object value = getFieldValue(entity, fieldName);
                    setCellValue(cell, value, fieldName, config);

                    // Установка стиля
                    if (dataStyles.containsKey(fieldName)) {
                        cell.setCellStyle(dataStyles.get(fieldName));
                    }

                    // Обновление ширины столбца
                    String stringValue = formatFieldValue(value, fieldName, config);
                    columnWidths[i] = Math.max(columnWidths[i], stringValue.length());
                }
            });

            // Автоматическая настройка ширины столбцов
            for (int i = 0; i < columnWidths.length; i++) {
                sheet.setColumnWidth(i, (columnWidths[i] + 2) * 256); // Excel specific width calculation
            }

            // Запись в выходной поток
            workbook.write(outputStream);
            outputStream.flush();

            // Обновляем прогресс
            progressTracker.complete(operationId, "Экспорт в Excel завершен успешно");

        } catch (IOException e) {
            log.error("Ошибка при записи данных в Excel: {}", e.getMessage(), e);
            progressTracker.error(operationId, "Ошибка при записи данных: " + e.getMessage());
            throw new RuntimeException("Ошибка при записи данных в Excel", e);
        }
    }

    /**
     * Создает стиль для заголовка таблицы
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * Создает стили для разных типов данных
     */
    private Map<String, CellStyle> createDataStyles(Workbook workbook, List<String> fieldNames) {
        Map<String, CellStyle> styles = new HashMap<>();

        // Стиль для дат
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy"));

        // Стиль для чисел
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        // Стиль для текста
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setWrapText(true);

        // Назначаем стили для полей в зависимости от их типа данных
        // Заглушка - в реальной реализации здесь будет логика определения типа полей

        return styles;
    }

    /**
     * Устанавливает значение ячейки в зависимости от типа данных
     */
    private void setCellValue(Cell cell, Object value, String fieldName, ExportConfig config) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }

        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else if (value instanceof java.time.LocalDate) {
            cell.setCellValue(java.sql.Date.valueOf((java.time.LocalDate) value));
        } else if (value instanceof java.time.LocalDateTime) {
            cell.setCellValue(java.sql.Timestamp.valueOf((java.time.LocalDateTime) value));
        } else if (value instanceof java.time.ZonedDateTime) {
            java.time.LocalDateTime ldt = ((java.time.ZonedDateTime) value).toLocalDateTime();
            cell.setCellValue(java.sql.Timestamp.valueOf(ldt));
        } else {
            // Для всех остальных типов используем строковое представление
            cell.setCellValue(formatFieldValue(value, fieldName, config));
        }
    }

    @Override
    public String getFileExtension() {
        return "xlsx";
    }
}