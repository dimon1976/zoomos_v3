// src/main/java/my/java/service/file/exporter/XlsxExporter.java
package my.java.service.file.exporter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.FileOperation;
import my.java.service.file.options.FileWritingOptions;
import my.java.util.PathResolver;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class XlsxExporter implements FileExporter {

    private final PathResolver pathResolver;
    private static final String[] SUPPORTED_TYPES = {"xlsx", "xls"};

    @Override
    public String[] getSupportedFileTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public boolean canExport(String fileType) {
        if (fileType == null) return false;

        String normalizedType = fileType.toLowerCase();
        for (String type : SUPPORTED_TYPES) {
            if (type.equals(normalizedType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Path exportData(
            List<Map<String, String>> data,
            List<String> fields,
            FileWritingOptions options,
            FileOperation operation) {

        try {
            // Создаем временный файл
            Path filePath = pathResolver.createTempFile("export", ".xlsx");

            // Начинаем этап экспорта
            operation.addStage("xlsx_export", "Экспорт данных в Excel");
            operation.updateStageProgress("xlsx_export", 0);

            try (Workbook workbook = new XSSFWorkbook();
                 FileOutputStream fileOut = new FileOutputStream(filePath.toFile())) {

                // Создаем лист
                String sheetName = options.getSheetName() != null ? options.getSheetName() : "Data";
                Sheet sheet = workbook.createSheet(sheetName);

                // Создаем стиль заголовков
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // Запись заголовков, если нужно
                if (options.isIncludeHeader()) {
                    Row headerRow = sheet.createRow(0);
                    for (int i = 0; i < fields.size(); i++) {
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(getDisplayName(fields.get(i)));
                        cell.setCellStyle(headerStyle);
                    }
                }

                // Запись данных
                int startRow = options.isIncludeHeader() ? 1 : 0;
                int totalRecords = data.size();

                for (int i = 0; i < totalRecords; i++) {
                    Map<String, String> rowData = data.get(i);
                    Row row = sheet.createRow(i + startRow);

                    for (int j = 0; j < fields.size(); j++) {
                        Cell cell = row.createCell(j);
                        String value = rowData.getOrDefault(fields.get(j), "");
                        cell.setCellValue(value);
                    }

                    // Обновляем прогресс
                    int progress = (int) (((double) (i + 1) / totalRecords) * 100);
                    operation.updateStageProgress("xlsx_export", progress);
                    operation.setProcessingProgress(progress);
                }

                // Автоматическая подгонка ширины столбцов
                if (options.isAutoSizeColumns()) {
                    for (int i = 0; i < fields.size(); i++) {
                        sheet.autoSizeColumn(i);
                        // Добавляем дополнительный отступ
                        int width = sheet.getColumnWidth(i);
                        sheet.setColumnWidth(i, width + 512); // 512 ~ 2 символам
                    }
                }

                // Сохраняем файл
                workbook.write(fileOut);
            }

            operation.completeStage("xlsx_export");
            return filePath;
        } catch (Exception e) {
            log.error("Ошибка при экспорте в Excel: {}", e.getMessage(), e);
            operation.failStage("xlsx_export", e.getMessage());
            throw new FileOperationException("Ошибка при экспорте в Excel: " + e.getMessage(), e);
        }
    }

    /**
     * Преобразует имя поля в отображаемое название
     */
    private String getDisplayName(String field) {
        // Отделяем префикс сущности, если есть
        int dotIndex = field.lastIndexOf('.');
        if (dotIndex > 0) {
            field = field.substring(dotIndex + 1);
        }

        // Преобразуем camelCase в нормальный текст
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (i == 0) {
                formatted.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                formatted.append(' ').append(c);
            } else {
                formatted.append(c);
            }
        }

        return formatted.toString();
    }
}