package my.java.service.file.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.entity.CompetitorData;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.repository.FileOperationRepository;
import my.java.service.file.builder.EntitySetBuilderFactory;
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

    private static final String[] SUPPORTED_EXTENSIONS = new String[]{"xlsx", "xls"};
    private static final String[] SUPPORTED_MIME_TYPES = new String[]{
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel"
    };

    // Максимальное количество строк для проверки при поиске заголовков
    private static final int MAX_HEADER_ROW = 10;

    // Максимальное количество пустых строк подряд
    private static final int MAX_EMPTY_ROWS = 5;

    // Максимальное количество строк для образца
    private static final int SAMPLE_SIZE = 10;

    /**
     * Конструктор для Excel процессора.
     *
     * @param pathResolver утилита для работы с путями
     * @param transformerFactory фабрика трансформеров значений
     */
    @Autowired
    public ExcelFileProcessor(
            PathResolver pathResolver,
            ValueTransformerFactory transformerFactory,
            EntitySetBuilderFactory entitySetBuilderFactory,
            FileOperationRepository fileOperationRepository) {
        super(pathResolver, transformerFactory, entitySetBuilderFactory, fileOperationRepository);
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
    protected List<Map<String, String>> readFile(Path filePath, Map<String, String> params) throws IOException {
        log.debug("Чтение Excel файла: {}", filePath);

        // Определяем параметры чтения
        FileReadingOptions options = determineReadingOptions(params);

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
            String[] headers = extractHeaders(sheet.getRow(headerRowIndex));
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
                Map<String, String> rowData = rowToMap(row, headers);
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
    protected ImportableEntity createEntity(String entityType) {
        if (entityType == null) {
            return null;
        }

        switch (entityType.toLowerCase()) {
            case "product":
                Product product = new Product();
                product.setTransformerFactory(transformerFactory);
                return product;
            case "regiondata":
                RegionData regionData = new RegionData();
                regionData.setTransformerFactory(transformerFactory);
                return regionData;
            case "competitordata":
                CompetitorData competitorData = new CompetitorData();
                competitorData.setTransformerFactory(transformerFactory);
                return competitorData;
            default:
                log.warn("Неизвестный тип сущности: {}", entityType);
                return null;
        }
    }

    @Override
    public Map<String, Object> analyzeFile(Path filePath, Map<String, String> params) {
        log.debug("Анализ Excel файла: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is)) {

            // Получаем информацию о листах
            List<Map<String, Object>> sheetsInfo = getWorkbookInfo(workbook);
            result.put("sheets", sheetsInfo);

            // Определяем параметры чтения
            FileReadingOptions options = determineReadingOptions(params);
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

            String[] headers = extractHeaders(sheet.getRow(headerRowIndex));
            result.put("headers", headers);

            // Получаем образец данных
            List<Map<String, String>> sampleData = getSampleData(sheet, headerRowIndex, headers);
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
    public boolean validateFile(Path filePath) {
        log.debug("Валидация Excel файла: {}", filePath);

        if (!Files.exists(filePath)) {
            log.error("Файл не существует: {}", filePath);
            return false;
        }

        if (!Files.isRegularFile(filePath)) {
            log.error("Путь не является файлом: {}", filePath);
            return false;
        }

        if (!Files.isReadable(filePath)) {
            log.error("Файл не доступен для чтения: {}", filePath);
            return false;
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            // Проверяем, можно ли открыть файл как Excel
            Workbook workbook = WorkbookFactory.create(is);

            // Проверяем, есть ли в файле хотя бы один лист
            if (workbook.getNumberOfSheets() == 0) {
                log.error("В файле нет листов");
                return false;
            }

            // Проверяем, есть ли в файле данные
            Sheet sheet = workbook.getSheetAt(0);
            return sheet.getLastRowNum() > 0;
        } catch (Exception e) {
            log.error("Ошибка при валидации Excel файла: {}", e.getMessage(), e);
            return false;
        }
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
            int headerRowIndex = determineHeaderRowIndex(sheet, new FileReadingOptions());
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
     * Определяет параметры чтения Excel файла.
     *
     * @param params параметры, указанные пользователем
     * @return настроенные параметры чтения
     */
    private FileReadingOptions determineReadingOptions(Map<String, String> params) {
        FileReadingOptions options = new FileReadingOptions();

        // Если пользователь указал параметры, используем их
        if (params != null) {
            if (params.containsKey("sheetName")) {
                options.setSheetName(params.get("sheetName"));
            }

            if (params.containsKey("sheetIndex")) {
                options.setSheetIndex(Integer.parseInt(params.get("sheetIndex")));
            }

            if (params.containsKey("headerRow")) {
                options.setHeaderRow(Integer.parseInt(params.get("headerRow")));
            }

            if (params.containsKey("trimWhitespace")) {
                options.setTrimWhitespace(Boolean.parseBoolean(params.get("trimWhitespace")));
            }

            if (params.containsKey("ignoreEmptyRows")) {
                options.setIgnoreEmptyRows(Boolean.parseBoolean(params.get("ignoreEmptyRows")));
            }

            if (params.containsKey("dateFormat")) {
                options.setDateFormat(params.get("dateFormat"));
            }
        }

        return options;
    }

    /**
     * Выбирает лист для обработки на основе параметров.
     *
     * @param workbook книга Excel
     * @param options параметры чтения
     * @return выбранный лист
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
     *
     * @param workbook книга Excel
     * @return список с информацией о листах
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

            // Добавляем информацию о наличии данных
            sheetInfo.put("isEmpty", sheet.getLastRowNum() <= 0);

            sheetsInfo.add(sheetInfo);
        }

        return sheetsInfo;
    }

    /**
     * Определяет индекс строки с заголовками.
     *
     * @param sheet лист Excel
     * @param options параметры чтения
     * @return индекс строки с заголовками или -1, если не найден
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
     *
     * @param row строка для проверки
     * @return true, если строка похожа на заголовок
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
     *
     * @param headerRow строка с заголовками
     * @return массив заголовков
     */
    private String[] extractHeaders(Row headerRow) {
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
     *
     * @param row строка Excel
     * @param headers заголовки
     * @return карта с данными строки
     */
    private Map<String, String> rowToMap(Row row, String[] headers) {
        Map<String, String> rowData = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.getCell(i);
            String value = "";

            if (cell != null) {
                value = getCellValueAsString(cell);
            }

            rowData.put(headers[i], value);
        }

        return rowData;
    }

    /**
     * Получает значение ячейки в виде строки.
     *
     * @param cell ячейка Excel
     * @return значение ячейки в виде строки
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
     *
     * @param row строка для проверки
     * @return true, если строка пустая
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
     *
     * @param sheet лист Excel
     * @param headerRowIndex индекс строки с заголовками
     * @param headers заголовки
     * @return список образцов данных
     */
    private List<Map<String, String>> getSampleData(Sheet sheet, int headerRowIndex, String[] headers) {
        List<Map<String, String>> sampleData = new ArrayList<>();
        int dataStartRow = headerRowIndex + 1;

        for (int i = dataStartRow; i <= sheet.getLastRowNum() && sampleData.size() < SAMPLE_SIZE; i++) {
            Row row = sheet.getRow(i);
            if (row != null && !isEmptyRow(row)) {
                Map<String, String> rowData = rowToMap(row, headers);
                sampleData.add(rowData);
            }
        }

        return sampleData;
    }

    /**
     * Определяет типы данных в колонках на основе образца данных.
     *
     * @param sampleData образец данных
     * @return карта с типами данных для каждой колонки
     */
    private Map<String, String> detectColumnTypes(List<Map<String, String>> sampleData) {
        if (sampleData == null || sampleData.isEmpty()) {
            return Collections.emptyMap();
        }

        // Получаем список всех заголовков
        Set<String> headers = sampleData.get(0).keySet();
        Map<String, String> columnTypes = new HashMap<>();

        // Для каждой колонки определяем тип данных
        for (String header : headers) {
            String type = detectColumnType(sampleData, header);
            columnTypes.put(header, type);
        }

        return columnTypes;
    }

    /**
     * Определяет тип данных для колонки.
     *
     * @param sampleData образец данных
     * @param header заголовок колонки
     * @return определенный тип данных
     */
    private String detectColumnType(List<Map<String, String>> sampleData, String header) {
        boolean isAllEmpty = true;
        boolean couldBeInteger = true;
        boolean couldBeDouble = true;
        boolean couldBeBoolean = true;
        boolean couldBeDate = true;

        // Проверяем все значения в колонке
        for (Map<String, String> row : sampleData) {
            String value = row.get(header);

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            isAllEmpty = false;
            value = value.trim();

            // Проверяем, является ли значение целым числом
            if (couldBeInteger) {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    couldBeInteger = false;
                }
            }

            // Проверяем, является ли значение дробным числом
            if (couldBeDouble && !couldBeInteger) {
                try {
                    Double.parseDouble(value.replace(",", "."));
                } catch (NumberFormatException e) {
                    couldBeDouble = false;
                }
            }

            // Проверяем, является ли значение булевым
            if (couldBeBoolean) {
                couldBeBoolean = isBooleanValue(value);
            }

            // Проверяем, является ли значение датой
            if (couldBeDate) {
                couldBeDate = isDateValue(value);
            }
        }

        // Определяем тип на основе проверок
        if (isAllEmpty) {
            return "string";
        } else if (couldBeInteger) {
            return "integer";
        } else if (couldBeDouble) {
            return "double";
        } else if (couldBeBoolean) {
            return "boolean";
        } else if (couldBeDate) {
            return "date";
        } else {
            return "string";
        }
    }

    /**
     * Проверяет, является ли значение булевым.
     *
     * @param value значение для проверки
     * @return true, если значение булево
     */
    private boolean isBooleanValue(String value) {
        String lowerValue = value.toLowerCase();
        return lowerValue.equals("true") || lowerValue.equals("false") ||
                lowerValue.equals("yes") || lowerValue.equals("no") ||
                lowerValue.equals("да") || lowerValue.equals("нет") ||
                lowerValue.equals("1") || lowerValue.equals("0");
    }

    /**
     * Проверяет, является ли значение датой.
     *
     * @param value значение для проверки
     * @return true, если значение похоже на дату
     */
    private boolean isDateValue(String value) {
        // Простая проверка на наличие разделителей дат
        return value.matches(".*\\d+[./\\-]\\d+[./\\-]\\d+.*");
    }

    /**
     * Класс для хранения параметров чтения Excel файла.
     */
    private static class FileReadingOptions {
        private String sheetName;
        private int sheetIndex = -1;
        private int headerRow = -1;
        private boolean trimWhitespace = true;
        private boolean ignoreEmptyRows = true;
        private String dateFormat = "dd.MM.yyyy";

        // Геттеры и сеттеры
        public String getSheetName() {
            return sheetName;
        }

        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
        }

        public int getSheetIndex() {
            return sheetIndex;
        }

        public void setSheetIndex(int sheetIndex) {
            this.sheetIndex = sheetIndex;
        }

        public int getHeaderRow() {
            return headerRow;
        }

        public void setHeaderRow(int headerRow) {
            this.headerRow = headerRow;
        }

        public boolean isTrimWhitespace() {
            return trimWhitespace;
        }

        public void setTrimWhitespace(boolean trimWhitespace) {
            this.trimWhitespace = trimWhitespace;
        }

        public boolean isIgnoreEmptyRows() {
            return ignoreEmptyRows;
        }

        public void setIgnoreEmptyRows(boolean ignoreEmptyRows) {
            this.ignoreEmptyRows = ignoreEmptyRows;
        }

        public String getDateFormat() {
            return dateFormat;
        }

        public void setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
        }
    }
}