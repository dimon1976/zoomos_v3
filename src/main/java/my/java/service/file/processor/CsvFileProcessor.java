// src/main/java/my/java/service/file/processor/CsvFileProcessor.java
package my.java.service.file.processor;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
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

    private static final String[] SUPPORTED_EXTENSIONS = {"csv", "txt"};
    private static final String[] SUPPORTED_MIME_TYPES = {"text/csv", "text/plain"};
    private static final char[] POSSIBLE_DELIMITERS = {',', ';', '\t', '|'};
    private static final int SAMPLE_SIZE = 10;
    private static final int MAX_HEADER_ROW = 10;

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
        if (filePath == null || !Files.exists(filePath)) return false;

        String fileName = filePath.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith("." + ext)) return true;
        }
        return false;
    }

    @Override
    protected List<Map<String, String>> readFileWithOptions(Path filePath, FileReadingOptions options) throws IOException {
        // Дополнительно определяем параметры, если они не заданы
        detectFileOptionsIfNeeded(filePath, options);

        // Создаем CSV Parser
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
            String[] headers = readHeaders(csvReader, options);
            if (headers == null || headers.length == 0) {
                throw new FileOperationException("Не удалось определить заголовки в CSV файле");
            }

            // Читаем данные
            String[] record;
            while ((record = csvReader.readNext()) != null) {
                if (options.isSkipEmptyRows() && isEmptyRecord(record)) continue;

                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String value = i < record.length ? record[i] : "";
                    if (options.isTrimWhitespace() && value != null) {
                        value = value.trim();
                    }
                    row.put(headers[i], value);
                }
                result.add(row);
            }
        } catch (CsvException e) {
            throw new IOException("Ошибка при чтении CSV файла: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    protected void validateFileType(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith("." + ext)) {
                return;
            }
        }
        throw new FileOperationException("Неподдерживаемый тип файла. Ожидается: " +
                String.join(", ", SUPPORTED_EXTENSIONS));
    }

    @Override
    public Map<String, Object> analyzeFileWithOptions(Path filePath, FileReadingOptions options) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Дополнительно определяем параметры, если они не заданы
            detectFileOptionsIfNeeded(filePath, options);
            result.put("detectedOptions", options);

            // Считываем образец данных
            List<Map<String, String>> sampleData = getSampleData(filePath, options);
            if (sampleData.isEmpty()) {
                result.put("error", "Не удалось получить образец данных");
                return result;
            }

            // Получаем заголовки
            result.put("headers", sampleData.get(0).keySet().toArray(new String[0]));
            result.put("sampleData", sampleData);

            // Оцениваем количество строк
            result.put("estimatedRows", estimateRecordCount(filePath));

            // Определяем типы данных
            result.put("columnTypes", detectColumnTypes(sampleData));

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public int estimateRecordCount(Path filePath) {
        try {
            // Получаем размер файла
            long fileSize = Files.size(filePath);

            // Считываем часть файла для определения средней длины строки
            FileReadingOptions options = new FileReadingOptions();
            detectFileOptionsIfNeeded(filePath, options);

            int sampleLines = 100;
            long sampleSize = 0;

            try (BufferedReader reader = Files.newBufferedReader(filePath, options.getCharset())) {
                String line;
                int lineCount = 0;

                while ((line = reader.readLine()) != null && lineCount < sampleLines) {
                    sampleSize += line.length() + 1; // +1 для символа новой строки
                    lineCount++;
                }

                if (lineCount == 0) return 0;

                // Вычисляем среднюю длину строки
                double avgLineLength = (double) sampleSize / lineCount;

                // Оцениваем общее количество строк, вычитая строку заголовка
                return Math.max(0, (int) (fileSize / avgLineLength) - (options.getHeaderRow() + 1));
            }
        } catch (Exception e) {
            log.warn("Ошибка при оценке количества записей: {}", e.getMessage());
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
        params.put("delimiter", "Разделитель полей (по умолчанию: ,)");
        params.put("quoteChar", "Символ кавычек (по умолчанию: \")");
        params.put("charset", "Кодировка файла (по умолчанию: UTF-8)");
        params.put("headerRow", "Номер строки заголовка (с 0, по умолчанию: 0)");
        params.put("skipEmptyRows", "Пропускать пустые строки (по умолчанию: true)");
        params.put("trimWhitespace", "Удалять пробельные символы (по умолчанию: true)");
        return params;
    }

    /**
     * Получает образец данных из файла
     */
    private List<Map<String, String>> getSampleData(Path filePath, FileReadingOptions options) {
        try {
            // Считываем первые несколько строк данных
            List<Map<String, String>> allData = readRawDataWithOptions(filePath, options);
            int sampleSize = Math.min(SAMPLE_SIZE, allData.size());
            return allData.subList(0, sampleSize);
        } catch (Exception e) {
            log.warn("Ошибка при получении образца данных: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Определяет, является ли запись пустой
     */
    private boolean isEmptyRecord(String[] record) {
        if (record == null || record.length == 0) return true;

        for (String value : record) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Читает заголовки из CSV-ридера
     */
    private String[] readHeaders(CSVReader csvReader, FileReadingOptions options) throws IOException, CsvException {
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
    }

    /**
     * Определяет параметры чтения CSV файла, если они не заданы
     */
    private void detectFileOptionsIfNeeded(Path filePath, FileReadingOptions options) throws IOException {
        // Определяем кодировку, если не задана
        if (options.getCharset() == null) {
            options.setCharset(detectCharset(filePath));
        }

        // Определяем разделитель и символ кавычек, если не заданы
        List<String> sampleLines = readSampleLines(filePath, options.getCharset(), 5);
        if (!sampleLines.isEmpty()) {
            // Разделитель
            if (options.getDelimiter() == null || options.getDelimiter() == ',') {
                options.setDelimiter(detectDelimiter(sampleLines));
            }

            // Символ кавычек
            if (options.getQuoteChar() == null || options.getQuoteChar() == '"') {
                options.setQuoteChar(detectQuoteChar(sampleLines, options.getDelimiter()));
            }

            // Строка заголовка
            if (options.getHeaderRow() < 0) {
                options.setHeaderRow(detectHeaderRow(sampleLines, options.getDelimiter(), options.getQuoteChar()));
            }
        }
    }

    /**
     * Определяет кодировку файла
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

        return StandardCharsets.UTF_8;
    }

    /**
     * Читает первые несколько строк файла для анализа
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
     * Определяет разделитель полей в CSV файле
     */
    private char detectDelimiter(List<String> sampleLines) {
        if (sampleLines.isEmpty()) {
            return ',';
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
                .orElse(',');
    }

    /**
     * Подсчитывает количество вхождений разделителя в строке
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
     * Определяет символ кавычек в CSV файле
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
        return '"';
    }

    /**
     * Проверяет, подходит ли указанный символ кавычек для данного образца строк
     */
    private boolean isQuoteCharSuitable(List<String> sampleLines, char delimiter, char quoteChar) {
        for (String line : sampleLines) {
            String[] parts = line.split(String.valueOf(delimiter), -1);

            for (String part : parts) {
                String trimmed = part.trim();

                // Проверяем, обрамлено ли значение кавычками
                if (trimmed.length() >= 2 && trimmed.charAt(0) == quoteChar &&
                        trimmed.charAt(trimmed.length() - 1) == quoteChar) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Определяет номер строки с заголовками
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
                // Заголовки обычно содержат буквы и цифры и не содержат много пробелов
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
     * Разбивает строку CSV с учетом кавычек
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
}