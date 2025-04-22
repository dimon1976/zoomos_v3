package my.java.service.file.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.service.file.options.FileReadingOptions;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.util.PathResolver;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Процессор для обработки Excel-файлов (XLSX и XLS).
 */
@Component
@Slf4j
public class ExcelFileProcessor extends AbstractFileProcessor {

    private static final String[] SUPPORTED_EXTENSIONS = {"xlsx", "xls"};
    private static final String[] SUPPORTED_MIME_TYPES = {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel"
    };
    private static final int MAX_HEADER_ROW = 10;
    private static final int MAX_EMPTY_ROWS = 5;
    private static final int SAMPLE_SIZE = 10;

    @Autowired
    public ExcelFileProcessor(PathResolver pathResolver, ValueTransformerFactory transformerFactory) {
        super(pathResolver, transformerFactory);
    }

    @Override
    public String[] getSupportedFileExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public String[] getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    @Override
    public boolean canProcess(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return false;
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith("." + ext)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected List<Map<String, String>> readFileWithOptions(Path filePath, FileReadingOptions options) throws IOException {
        log.debug("Чтение Excel файла с FileReadingOptions: {}", filePath);

        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is)) {

            // Выбираем лист для обработки
            Sheet sheet = selectSheet(workbook, options);

            if (sheet == null) {
                throw new FileOperationException("Не найден лист для обработки в Excel файле");
            }

            // Определяем строку с заголовками
            int headerRowIndex = determineHeaderRowIndex(sheet, options);
            if (headerRowIndex < 0) {
                throw new FileOperationException("Не удалось определить заголовки в Excel файле");
            }

            // Получаем заголовки
            String[] headers = extractHeaders(sheet.getRow(headerRowIndex), options);
            if (headers == null || headers.length == 0) {
                throw new FileOperationException("Не удалось извлечь заголовки из Excel файла");
            }

            // Читаем данные
            List<Map<String, String>> result = new ArrayList<>();
            int dataStartRow = headerRowIndex + 1;
            int emptyRowCount = 0;

            for (int i = dataStartRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                // Проверяем, не пустая ли строка
                if (row == null || isEmptyRow(row)) {
                    emptyRowCount++;
                    if (emptyRowCount >= MAX_EMPTY_ROWS) {
                        // Если обнаружено несколько пустых строк подряд, прекращаем чтение
                        break;
                    }
                    continue;
                }

                // Сбрасываем счетчик пустых строк
                emptyRowCount = 0;

                // Преобразуем строку в Map
                Map<String, String> rowData = rowToMap(row, headers, options);
                result.add(rowData);
            }

            log.debug("Прочитано {} записей из Excel файла", result.size());
            return result;
        } catch (Exception e) {
            log.error("Ошибка при чтении Excel файла: {}", e.getMessage(), e);
            throw new IOException("Ошибка при чтении Excel файла: " + e.getMessage(), e);
        }
    }

    @Override
    protected void validateFileType(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        boolean isValid = false;

        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith("." + ext)) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            throw new FileOperationException("Неподдерживаемый тип файла. Ожидается Excel файл (XLSX или XLS).");
        }
    }

    @Override
    public List<Map<String, String>> readRawDataWithOptions(Path filePath, FileReadingOptions options) {
        try {
            return readFileWithOptions(filePath, options);
        } catch (IOException e) {
            log.error("Ошибка при чтении сырых данных: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при чтении сырых данных: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> analyzeFileWithOptions(Path filePath, FileReadingOptions options) {
        log.debug("Анализ Excel файла: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is)) {

            // Получаем информацию о листах
            List<Map<String, Object>> sheetsInfo = getWorkbookInfo(workbook);
            result.put("sheets", sheetsInfo);

            // Сохраняем параметры
            result.put("options", options);

            // Выбираем лист для анализа
            Sheet sheet = selectSheet(workbook, options);
            if (sheet == null) {
                result.put("error", "Не найден лист для анализа");
                return result;
            }

            // Определяем заголовки
            int headerRowIndex = determineHeaderRowIndex(sheet, options);
            if (headerRowIndex < 0) {
                result.put("error", "Не удалось определить заголовки");
                return result;
            }

            String[] headers = extractHeaders(sheet.getRow(headerRowIndex), options);
            result.put("headers", headers);

            // Получаем образец данных
            List<Map<String, String>> sampleData = getSampleData(sheet, headerRowIndex, headers, options);
            result.put("sampleData", sampleData);

            // Определяем приблизительное количество строк
            int rowCount = sheet.getLastRowNum() - headerRowIndex;
            result.put("estimatedRows", rowCount > 0 ? rowCount : 0);

            // Определяем типы данных в колонках
            Map<String, String> columnTypes = detectColumnTypes(sampleData);
            result.put("columnTypes", columnTypes);

        } catch (Exception e) {
            log.error("Ошибка при анализе Excel файла: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public int estimateRecordCount(Path filePath) {
        log.debug("Оценка количества записей в Excel файле: {}", filePath);

        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is)) {

            // Выбираем лист для анализа
            Sheet sheet = workbook.getSheetAt(0); // По умолчанию первый лист

            if (sheet == null) {
                return 0;
            }

            // Определяем строку с заголовками
            FileReadingOptions options = new FileReadingOptions();
            int headerRowIndex = determineHeaderRowIndex(sheet, options);
            if (headerRowIndex < 0) {
                return 0;
            }

            // Оцениваем количество строк с данными
            return sheet.getLastRowNum() - headerRowIndex;
        } catch (Exception e) {
            log.error("Ошибка при оценке количества записей: {}", e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public boolean supportsStreaming() {
        return false; // Excel API не поддерживает потоковую обработку
    }

    @Override
    public Map<String, Object> getConfigParameters() {
        Map<String, Object> params = new HashMap<>();

        params.put("sheetName", "Имя листа для обработки (по умолчанию: первый лист)");
        params.put("sheetIndex", "Индекс листа для обработки (с 0, по умолчанию: 0)");
        params.put("headerRow", "Номер строки заголовка (с 0, по умолчанию: автоопределение)");
        params.put("trimWhitespace", "Удалять пробельные символы (по умолчанию: true)");
        params.put("ignoreEmptyRows", "Игнорировать пустые строки (по умолчанию: true)");
        params.put("dateFormat", "Формат даты для преобразования (по умолчанию: dd.MM.yyyy)");

        return params;
    }

    /**
     * Выбирает лист для обработки на основе параметров.
     */
    private Sheet selectSheet(Workbook workbook, FileReadingOptions options) {
        // Если указано имя листа, ищем по имени
        if (options.getSheetName() != null && !options.getSheetName().isEmpty()) {
            Sheet sheet = workbook.getSheet(options.getSheetName());
            if (sheet != null) {
                return sheet;
            }
            log.warn("Лист с именем '{}' не найден, используем первый лист", options.getSheetName());
        }

        // Если указан индекс листа, используем его
        if (options.getSheetIndex() >= 0 && options.getSheetIndex() < workbook.getNumberOfSheets()) {
            return workbook.getSheetAt(options.getSheetIndex());
        }

        // По умолчанию используем первый лист
        if (workbook.getNumberOfSheets() > 0) {
            return workbook.getSheetAt(0);
        }

        return null;
    }

    /**
     * Получает информацию обо всех листах в книге.
     */
    private List<Map<String, Object>> getWorkbookInfo(Workbook workbook) {
        List<Map<String, Object>> sheetsInfo = new ArrayList<>();

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            Map<String, Object> sheetInfo = new HashMap<>();

            sheetInfo.put("index", i);
            sheetInfo.put("name", sheet.getSheetName());
            sheetInfo.put("rowCount", sheet.getLastRowNum() + 1);

            // Определяем количество столбцов
            int columnCount = 0;
            for (int j = 0; j <= Math.min(10, sheet.getLastRowNum()); j++) {
                Row row = sheet.getRow(j);
                if (row != null) {
                    columnCount = Math.max(columnCount, row.getLastCellNum());
                }
            }

            sheetInfo.put("columnCount", columnCount);
            sheetInfo.put("isEmpty", sheet.getLastRowNum() <= 0);

            sheetsInfo.add(sheetInfo);
        }

        return sheetsInfo;
    }

    /**
     * Определяет индекс строки с заголовками.
     */
    private int determineHeaderRowIndex(Sheet sheet, FileReadingOptions options) {
        // Если указан индекс заголовка, используем его
        if (options.getHeaderRow() >= 0) {
            return options.getHeaderRow();
        }

        // Иначе пытаемся определить заголовки автоматически
        for (int i = 0; i <= Math.min(MAX_HEADER_ROW, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row != null && isHeaderRow(row)) {
                return i;
            }
        }

        // Если не удалось определить заголовки, используем первую строку
        return 0;
    }

    /**
     * Проверяет, является ли строка заголовком.
     */
    private boolean isHeaderRow(Row row) {
        if (row == null || row.getLastCellNum() <= 0) {
            return false;
        }

        // Заголовки обычно содержат текстовые значения
        int textCellCount = 0;

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue();
                if (value != null && !value.trim().isEmpty()) {
                    textCellCount++;
                }
            }
        }

        // Если большинство ячеек содержат текст, считаем строку заголовком
        return textCellCount > 0 && (double) textCellCount / row.getLastCellNum() > 0.5;
    }

    /**
     * Извлекает заголовки из строки.
     */
    private String[] extractHeaders(Row headerRow, FileReadingOptions options) {
        if (headerRow == null) {
            return new String[0];
        }

        int lastCellNum = headerRow.getLastCellNum();
        String[] headers = new String[lastCellNum];

        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String header = getCellValueAsString(cell);

                // Если заголовок пустой, генерируем имя
                if (header == null || header.trim().isEmpty()) {
                    header = "Column" + (i + 1);
                } else if (options.isTrimWhitespace()) {
                    header = header.trim();
                }

                headers[i] = header;
            } else {
                headers[i] = "Column" + (i + 1);
            }
        }

        return headers;
    }

    /**
     * Преобразует строку Excel в карту.
     */
    private Map<String, String> rowToMap(Row row, String[] headers, FileReadingOptions options) {
        Map<String, String> rowData = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.getCell(i);
            String value = "";

            if (cell != null) {
                value = getCellValueAsString(cell);
                if (options.isTrimWhitespace() && value != null) {
                    value = value.trim();
                }
            }

            rowData.put(headers[i], value);
        }

        return rowData;
    }

    /**
     * Получает значение ячейки в виде строки.
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Для чисел с плавающей точкой используем форматирование
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        // Целое число
                        return String.valueOf((long) value);
                    } else {
                        // Число с плавающей точкой
                        return String.valueOf(value);
                    }
                }

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());

            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }

            case BLANK:
                return "";

            default:
                return "";
        }
    }

    /**
     * Проверяет, является ли строка Excel пустой.
     */
    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Получает образец данных из листа Excel.
     */
    private List<Map<String, String>> getSampleData(Sheet sheet, int headerRowIndex, String[] headers, FileReadingOptions options) {
        List<Map<String, String>> sampleData = new ArrayList<>();
        int dataStartRow = headerRowIndex + 1;

        for (int i = dataStartRow; i <= sheet.getLastRowNum() && sampleData.size() < SAMPLE_SIZE; i++) {
            Row row = sheet.getRow(i);
            if (row != null && !isEmptyRow(row)) {
                Map<String, String> rowData = rowToMap(row, headers, options);
                sampleData.add(rowData);
            }
        }

        return sampleData;
    }
}