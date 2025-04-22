package my.java.service.file.processor;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.service.file.options.FileReadingOptions;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.util.PathResolver;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Процессор для обработки CSV-файлов.
 */
@Component
@Slf4j
public class CsvFileProcessor extends AbstractFileProcessor {

    private static final String[] SUPPORTED_EXTENSIONS = new String[]{"csv", "txt"};
    private static final String[] SUPPORTED_MIME_TYPES = new String[]{"text/csv", "text/plain"};

    // Возможные разделители для автоопределения
    private static final char[] POSSIBLE_DELIMITERS = {',', ';', '\t', '|'};

    // Параметры по умолчанию
    private static final char DEFAULT_DELIMITER = ',';
    private static final char DEFAULT_QUOTE = '"';
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final int SAMPLE_SIZE = 10000; // Размер выборки для анализа
    private static final int MAX_HEADER_ROW = 10; // Максимальный номер строки для поиска заголовков

    // Константы для имен параметров
    private static final String PARAM_DELIMITER = "delimiter";
    private static final String PARAM_QUOTE_CHAR = "quoteChar";
    private static final String PARAM_CHARSET = "charset";
    private static final String PARAM_HEADER_ROW = "headerRow";
    private static final String PARAM_SKIP_EMPTY_ROWS = "skipEmptyRows";
    private static final String PARAM_TRIM_WHITESPACE = "trimWhitespace";

    /**
     * Конструктор для CSV процессора.
     *
     * @param pathResolver       утилита для работы с путями
     * @param transformerFactory фабрика трансформеров значений
     */
    @Autowired
    public CsvFileProcessor(PathResolver pathResolver, ValueTransformerFactory transformerFactory) {
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

        // Можно добавить дополнительную проверку содержимого файла
        return false;
    }

    @Override
    protected List<Map<String, String>> readFile(Path filePath, Map<String, String> params) throws IOException {
        log.debug("Чтение CSV файла: {}", filePath);

        // Определяем параметры чтения
        FileReadingOptions options = determineReadingOptions(filePath, params);

        // Создаем CSVParser с настроенными параметрами
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(options.getDelimiter())
                .withQuoteChar(options.getQuoteChar())
                .withIgnoreQuotations(false)
                .build();

        List<Map<String, String>> result = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(filePath, options.getCharset());
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(parser)
                     .withSkipLines(options.getHeaderRow())
                     .build()) {

            // Читаем заголовки
            String[] headers = determineHeaders(filePath, options);
            if (headers == null || headers.length == 0) {
                throw new FileOperationException("Не удалось определить заголовки в CSV файле: " + filePath);
            }

            // Читаем данные
            String[] record;
            int rowNumber = options.getHeaderRow() + 1; // строки начинаются с 1

            while ((record = csvReader.readNext()) != null) {
                Map<String, String> row = new HashMap<>();

                // Преобразуем запись в Map
                for (int i = 0; i < headers.length; i++) {
                    if (i < record.length) {
                        row.put(headers[i], record[i]);
                    } else {
                        // Если значение отсутствует, добавляем пустую строку
                        row.put(headers[i], "");
                    }
                }

                result.add(row);
                rowNumber++;
            }

            log.debug("Прочитано {} записей из CSV файла", result.size());
        } catch (CsvException e) {
            log.error("Ошибка при чтении CSV файла: {}", e.getMessage());
            throw new IOException("Ошибка при чтении CSV файла: " + e.getMessage(), e);
        }

        return result;
    }

    @Override
    protected void validateFileType(Path filePath) {
        if (filePath == null) {
            throw new FileOperationException("Путь к файлу не может быть null");
        }

        if (!Files.exists(filePath)) {
            throw new FileOperationException("Файл не существует: " + filePath);
        }

        if (!Files.isRegularFile(filePath)) {
            throw new FileOperationException("Указанный путь не является файлом: " + filePath);
        }

        if (!Files.isReadable(filePath)) {
            throw new FileOperationException("Файл не доступен для чтения: " + filePath);
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        boolean isValidExtension = false;

        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith("." + ext)) {
                isValidExtension = true;
                break;
            }
        }

        if (!isValidExtension) {
            throw new FileOperationException("Неподдерживаемый тип файла. Ожидается файл с расширением: "
                    + String.join(", ", SUPPORTED_EXTENSIONS));
        }
    }

    @Override
    public List<Map<String, String>> readRawDataWithOptions(Path filePath, FileReadingOptions options) {
        log.debug("Чтение сырых данных из CSV файла с FileReadingOptions: {}", filePath);

        try {
            // Преобразуем options в Map для использования с существующими методами
            Map<String, String> params = options.toMap();

            // Используем существующий метод
            return readFile(filePath, params);
        } catch (IOException e) {
            log.error("Ошибка при чтении сырых данных: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при чтении сырых данных: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> analyzeFile(Path filePath, Map<String, String> params) {
        log.debug("Анализ CSV файла: {}", filePath);

        Map<String, Object> result = new HashMap<>();

        try {
            // Определяем параметры чтения
            FileReadingOptions options = determineReadingOptions(filePath, params);
            result.put("detectedOptions", options);

            // Определяем заголовки
            String[] headers = determineHeaders(filePath, options);
            result.put("headers", headers);

            // Получаем образец данных
            List<Map<String, String>> sampleData = getSampleData(filePath, options, headers);
            result.put("sampleData", sampleData);

            // Оцениваем количество строк
            int estimatedRows = estimateRecordCount(filePath);
            result.put("estimatedRows", estimatedRows);

            // Определяем типы данных в колонках
            Map<String, String> columnTypes = detectColumnTypes(sampleData);
            result.put("columnTypes", columnTypes);

        } catch (Exception e) {
            log.error("Ошибка при анализе CSV файла: {}", e.getMessage());
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public boolean validateFile(Path filePath) {
        log.debug("Валидация CSV файла: {}", filePath);

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

        try {
            // Проверяем, можно ли прочитать заголовки
            FileReadingOptions options = determineReadingOptions(filePath, null);
            String[] headers = determineHeaders(filePath, options);

            return headers != null && headers.length > 0;
        } catch (Exception e) {
            log.error("Ошибка при валидации CSV файла: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public int estimateRecordCount(Path filePath) {
        log.debug("Оценка количества записей в CSV файле: {}", filePath);

        try {
            // Получаем размер файла
            long fileSize = Files.size(filePath);

            // Читаем небольшую часть файла для определения средней длины строки
            int sampleLines = 100;
            long sampleSize = 0;

            FileReadingOptions options = determineReadingOptions(filePath, null);
            try (BufferedReader reader = Files.newBufferedReader(filePath, options.getCharset())) {
                String line;
                int lineCount = 0;

                while ((line = reader.readLine()) != null && lineCount < sampleLines) {
                    sampleSize += line.length() + 1; // +1 для символа новой строки
                    lineCount++;
                }

                if (lineCount == 0) {
                    return 0;
                }

                // Вычисляем среднюю длину строки
                double avgLineLength = (double) sampleSize / lineCount;

                // Оцениваем общее количество строк, вычитая строку заголовка
                return Math.max(0, (int) (fileSize / avgLineLength) - (options.getHeaderRow() + 1));
            }
        } catch (Exception e) {
            log.error("Ошибка при оценке количества записей: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Map<String, Object> getConfigParameters() {
        Map<String, Object> params = new HashMap<>();

        params.put(PARAM_DELIMITER, "Разделитель полей (по умолчанию: ,)");
        params.put(PARAM_QUOTE_CHAR, "Символ кавычек (по умолчанию: \")");
        params.put(PARAM_CHARSET, "Кодировка файла (по умолчанию: UTF-8)");
        params.put(PARAM_HEADER_ROW, "Номер строки заголовка (с 0, по умолчанию: 0)");
        params.put(PARAM_SKIP_EMPTY_ROWS, "Пропускать пустые строки (по умолчанию: true)");
        params.put(PARAM_TRIM_WHITESPACE, "Удалять пробельные символы (по умолчанию: true)");

        return params;
    }

    /**
     * Безопасно извлекает значение параметра из Map.
     *
     * @param params       Map с параметрами
     * @param key          ключ параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра или значение по умолчанию, если параметр отсутствует
     */
    private String getParameterSafely(Map<String, String> params, String key, String defaultValue) {
        if (params == null || !params.containsKey(key) || params.get(key) == null || params.get(key).isEmpty()) {
            return defaultValue;
        }
        return params.get(key);
    }

    /**
     * Безопасно извлекает целочисленное значение параметра.
     *
     * @param params       Map с параметрами
     * @param key          ключ параметра
     * @param defaultValue значение по умолчанию
     * @return целочисленное значение параметра или значение по умолчанию при ошибке
     */
    private int getIntParameterSafely(Map<String, String> params, String key, int defaultValue) {
        String value = getParameterSafely(params, key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Не удалось преобразовать параметр {} в число: {}", key, value);
            return defaultValue;
        }
    }

    /**
     * Безопасно извлекает символьное значение параметра.
     *
     * @param params       Map с параметрами
     * @param key          ключ параметра
     * @param defaultValue значение по умолчанию
     * @return символьное значение параметра или значение по умолчанию при ошибке
     */
    private char getCharParameterSafely(Map<String, String> params, String key, char defaultValue) {
        String value = getParameterSafely(params, key, String.valueOf(defaultValue));
        return value.isEmpty() ? defaultValue : value.charAt(0);
    }

    /**
     * Определяет параметры чтения CSV файла, комбинируя автоопределение и пользовательские настройки.
     *
     * @param filePath путь к файлу
     * @param params   параметры, указанные пользователем
     * @return настроенные параметры чтения
     */
    private FileReadingOptions determineReadingOptions(Path filePath, Map<String, String> params) {
        // Создаем основной объект параметров из Map
        FileReadingOptions options = params != null ?
                FileReadingOptions.fromMap(params) : new FileReadingOptions();

        // Пытаемся автоматически определить параметры, если они не указаны явно
        try {
            detectFileOptions(filePath, options);
        } catch (IOException e) {
            log.error("Ошибка при автоопределении параметров файла: {}", e.getMessage());
        }

        return options;
    }

    /**
     * Автоматически определяет параметры CSV файла.
     *
     * @param filePath путь к файлу
     * @param options  объект для сохранения определенных параметров
     * @throws IOException если возникла ошибка при чтении файла
     */
    private void detectFileOptions(Path filePath, FileReadingOptions options) throws IOException {
        // Определяем кодировку
        Charset charset = detectCharset(filePath);
        options.setCharset(charset);

        // Читаем первые несколько строк для анализа
        List<String> sampleLines = readSampleLines(filePath, charset, 5);
        if (sampleLines.isEmpty()) {
            return;
        }

        // Определяем разделитель
        char delimiter = detectDelimiter(sampleLines);
        options.setDelimiter(delimiter);

        // Определяем символ кавычек (по умолчанию используем двойные кавычки)
        options.setQuoteChar(detectQuoteChar(sampleLines, delimiter));

        // Определяем номер строки заголовка
        int headerRow = detectHeaderRow(sampleLines, delimiter, options.getQuoteChar());
        options.setHeaderRow(headerRow);
    }

    /**
     * Определяет кодировку файла.
     *
     * @param filePath путь к файлу
     * @return определенная кодировка
     * @throws IOException если возникла ошибка при чтении файла
     */
    private Charset detectCharset(Path filePath) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[4096];
        int nread;

        try (InputStream fis = Files.newInputStream(filePath)) {
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
        }

        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        detector.reset();

        if (encoding != null) {
            try {
                return Charset.forName(encoding);
            } catch (Exception e) {
                log.warn("Неизвестная кодировка: {}, используем UTF-8", encoding);
            }
        }

        return DEFAULT_CHARSET;
    }

    /**
     * Читает первые несколько строк файла для анализа.
     *
     * @param filePath  путь к файлу
     * @param charset   кодировка файла
     * @param lineCount количество строк для чтения
     * @return список прочитанных строк
     * @throws IOException если возникла ошибка при чтении файла
     */
    private List<String> readSampleLines(Path filePath, Charset charset, int lineCount) throws IOException {
        List<String> sampleLines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null && count < lineCount) {
                if (!line.trim().isEmpty()) {
                    sampleLines.add(line);
                    count++;
                }
            }
        }

        return sampleLines;
    }

    /**
     * Определяет разделитель полей в CSV файле.
     *
     * @param sampleLines образец строк из файла
     * @return определенный разделитель
     */
    private char detectDelimiter(List<String> sampleLines) {
        if (sampleLines.isEmpty()) {
            return DEFAULT_DELIMITER;
        }

        // Подсчитываем частоту возможных разделителей
        Map<Character, Integer> delimiterCounts = new HashMap<>();

        for (String line : sampleLines) {
            for (char delimiter : POSSIBLE_DELIMITERS) {
                int count = countDelimiter(line, delimiter);
                delimiterCounts.merge(delimiter, count, Integer::sum);
            }
        }

        // Выбираем разделитель с наибольшей частотой
        return delimiterCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(DEFAULT_DELIMITER);
    }

    /**
     * Подсчитывает количество вхождений разделителя в строке.
     *
     * @param line      строка для анализа
     * @param delimiter разделитель
     * @return количество вхождений разделителя
     */
    private int countDelimiter(String line, char delimiter) {
        int count = 0;
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            // Учитываем символы в кавычках
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                count++;
            }
        }

        return count;
    }

    /**
     * Определяет символ кавычек в CSV файле.
     *
     * @param sampleLines образец строк из файла
     * @param delimiter   определенный разделитель
     * @return символ кавычек
     */
    private char detectQuoteChar(List<String> sampleLines, char delimiter) {
        // Сначала проверяем двойные кавычки (наиболее распространены)
        if (isQuoteCharSuitable(sampleLines, delimiter, '"')) {
            return '"';
        }

        // Затем одинарные кавычки
        if (isQuoteCharSuitable(sampleLines, delimiter, '\'')) {
            return '\'';
        }

        // По умолчанию используем двойные кавычки
        return DEFAULT_QUOTE;
    }

    /**
     * Проверяет, подходит ли указанный символ кавычек для данного образца строк.
     *
     * @param sampleLines образец строк
     * @param delimiter   разделитель
     * @param quoteChar   символ кавычек для проверки
     * @return true, если символ кавычек подходит
     */
    private boolean isQuoteCharSuitable(List<String> sampleLines, char delimiter, char quoteChar) {
        for (String line : sampleLines) {
            String[] parts = line.split(String.valueOf(delimiter), -1);

            for (String part : parts) {
                String trimmed = part.trim();

                // Проверяем, обрамлено ли значение кавычками
                if (trimmed.length() >= 2 && trimmed.charAt(0) == quoteChar && trimmed.charAt(trimmed.length() - 1) == quoteChar) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Определяет номер строки с заголовками.
     *
     * @param sampleLines образец строк
     * @param delimiter   разделитель
     * @param quoteChar   символ кавычек
     * @return номер строки с заголовками (с 0)
     */
    private int detectHeaderRow(List<String> sampleLines, char delimiter, char quoteChar) {
        if (sampleLines.isEmpty()) {
            return 0;
        }

        // Проверяем каждую строку как потенциальный заголовок
        for (int i = 0; i < Math.min(sampleLines.size(), MAX_HEADER_ROW); i++) {
            String line = sampleLines.get(i);
            String[] parts = splitCsvLine(line, delimiter, quoteChar);

            boolean isHeader = true;
            for (String part : parts) {
                // Заголовки обычно состоят из букв и цифр и не содержат много пробелов
                if (part.trim().isEmpty() || part.contains("  ") || !part.matches(".*[a-zA-Zа-яА-Я0-9].*")) {
                    isHeader = false;
                    break;
                }
            }

            if (isHeader) {
                return i;
            }
        }

        // Если не удалось определить, берем первую строку
        return 0;
    }

    /**
     * Разбивает строку CSV с учетом кавычек.
     *
     * @param line      строка для разбиения
     * @param delimiter разделитель
     * @param quoteChar символ кавычек
     * @return массив полей
     */
    private String[] splitCsvLine(String line, char delimiter, char quoteChar) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == quoteChar) {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    /**
     * Определяет заголовки CSV файла.
     *
     * @param filePath путь к файлу
     * @param options  параметры чтения
     * @return массив заголовков
     * @throws IOException если возникла ошибка при чтении файла
     */
    private String[] determineHeaders(Path filePath, FileReadingOptions options) throws IOException {
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(options.getDelimiter())
                .withQuoteChar(options.getQuoteChar())
                .build();

        try (Reader reader = Files.newBufferedReader(filePath, options.getCharset());
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(parser)
                     .withSkipLines(options.getHeaderRow())
                     .build()) {
            // Добавить прямо перед строкой String[] headers = csvReader.readNext();
            log.info("Чтение заголовков из файла: {}", filePath);
            log.info("Настройки чтения: разделитель='{}', кавычки='{}', строка заголовка={}, кодировка={}",
                    options.getDelimiter(), options.getQuoteChar(), options.getHeaderRow(), options.getCharset());
            try {
                // Попробуем прочитать первые несколько байт файла для диагностики
                byte[] firstBytes = Files.readAllBytes(filePath);
                String fileStart = new String(Arrays.copyOf(firstBytes, Math.min(100, firstBytes.length)), options.getCharset());
                log.info("Начало файла: [{}]", fileStart.replace("\n", "\\n").replace("\r", "\\r"));
            } catch (Exception e) {
                log.warn("Не удалось прочитать начало файла для диагностики: {}", e.getMessage());
            }
            String[] headers = csvReader.readNext();

            if (headers != null && options.isTrimWhitespace()) {
                // Удаляем пробелы и обрабатываем заголовки
                for (int i = 0; i < headers.length; i++) {
                    headers[i] = headers[i].trim();

                    // Если заголовок пустой, генерируем имя
                    if (headers[i].isEmpty()) {
                        headers[i] = "Column" + (i + 1);
                    }
                }
            }

            return headers;
        } catch (CsvException e) {
            log.error("Ошибка при определении заголовков: {}", e.getMessage());
            throw new IOException("Ошибка при определении заголовков: " + e.getMessage(), e);
        }
    }

    /**
     * Получает образец данных из файла.
     *
     * @param filePath путь к файлу
     * @param options  параметры чтения
     * @param headers  заголовки
     * @return список записей из файла
     * @throws IOException если возникла ошибка при чтении файла
     */
    private List<Map<String, String>> getSampleData(Path filePath, FileReadingOptions options, String[] headers)
            throws IOException {

        if (headers == null || headers.length == 0) {
            throw new FileOperationException("Не удалось определить заголовки");
        }

        CSVParser parser = new CSVParserBuilder()
                .withSeparator(options.getDelimiter())
                .withQuoteChar(options.getQuoteChar())
                .build();

        List<Map<String, String>> sampleData = new ArrayList<>();
        int sampleRows = 10; // Количество строк в образце

        try (Reader reader = Files.newBufferedReader(filePath, options.getCharset());
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(parser)
                     .withSkipLines(options.getHeaderRow() + 1) // +1 для пропуска самих заголовков
                     .build()) {

            String[] record;
            while ((record = csvReader.readNext()) != null && sampleData.size() < sampleRows) {
                if (options.isSkipEmptyRows() && isEmptyRecord(record)) {
                    continue;
                }

                Map<String, String> row = new HashMap<>();

                for (int i = 0; i < headers.length; i++) {
                    if (i < record.length) {
                        String value = record[i];
                        if (options.isTrimWhitespace()) {
                            value = value.trim();
                        }
                        row.put(headers[i], value);
                    } else {
                        row.put(headers[i], "");
                    }
                }

                sampleData.add(row);
            }

            return sampleData;
        } catch (CsvException e) {
            log.error("Ошибка при получении образца данных: {}", e.getMessage());
            throw new IOException("Ошибка при получении образца данных: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет, является ли запись пустой.
     *
     * @param record запись для проверки
     * @return true, если запись пустая
     */
    private boolean isEmptyRecord(String[] record) {
        if (record == null || record.length == 0) {
            return true;
        }

        for (String value : record) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }
}