// src/main/java/my/java/service/file/analyzer/CsvFileAnalyzer.java
package my.java.service.file.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для анализа CSV файлов и определения их параметров
 */
@Service
@Slf4j
public class CsvFileAnalyzer {

    private static final int SAMPLE_SIZE = 1024 * 10; // 10KB для анализа
    private static final int MAX_LINES_TO_ANALYZE = 50;

    // Возможные разделители
    private static final char[] POSSIBLE_DELIMITERS = {',', ';', '\t', '|', ':'};

    // Возможные кавычки
    private static final char[] POSSIBLE_QUOTES = {'"', '\''};

    /**
     * Анализирует CSV файл и определяет его параметры
     */
    public CsvAnalysisResult analyzeFile(Path filePath) {
        log.debug("Начинаем анализ файла: {}", filePath);

        try {
            // Определяем кодировку
            String encoding = detectEncoding(filePath);
            log.debug("Определена кодировка: {}", encoding);

            // Читаем образец файла
            List<String> sampleLines = readSampleLines(filePath, encoding);

            if (sampleLines.isEmpty()) {
                throw new IllegalArgumentException("Файл пуст или не может быть прочитан");
            }

            // Определяем разделитель
            char delimiter = detectDelimiter(sampleLines);
            log.debug("Определен разделитель: '{}'", delimiter);

            // Определяем символ кавычек
            char quoteChar = detectQuoteChar(sampleLines, delimiter);
            log.debug("Определен символ кавычек: '{}'", quoteChar);

            // Определяем символ экранирования
            char escapeChar = detectEscapeChar(sampleLines, quoteChar);

            // Анализируем заголовки
            List<String> headers = parseHeaders(sampleLines.get(0), delimiter, quoteChar, escapeChar);
            log.debug("Найдено заголовков: {}", headers.size());

            // Подсчитываем примерное количество строк
            long estimatedLines = estimateLineCount(filePath, encoding);

            // Получаем размер файла
            long fileSize = Files.size(filePath);

            CsvAnalysisResult result = CsvAnalysisResult.builder()
                    .encoding(encoding)
                    .delimiter(delimiter)
                    .quoteChar(quoteChar)
                    .escapeChar(escapeChar)
                    .headers(headers)
                    .estimatedLines(estimatedLines)
                    .fileSize(fileSize)
                    .sampleLines(sampleLines.subList(0, Math.min(5, sampleLines.size())))
                    .hasHeaders(true) // Предполагаем, что первая строка - заголовки
                    .build();

            log.info("Анализ файла завершен: {} строк, {} столбцов", estimatedLines, headers.size());
            return result;

        } catch (Exception e) {
            log.error("Ошибка при анализе файла {}: {}", filePath, e.getMessage(), e);
            throw new RuntimeException("Не удалось проанализировать файл: " + e.getMessage(), e);
        }
    }

    /**
     * Определяет кодировку файла
     */
    private String detectEncoding(Path filePath) throws IOException {
        byte[] buffer = new byte[SAMPLE_SIZE];

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            int bytesRead = inputStream.read(buffer);

            UniversalDetector detector = new UniversalDetector(null);
            detector.handleData(buffer, 0, bytesRead);
            detector.dataEnd();

            String detectedEncoding = detector.getDetectedCharset();
            detector.reset();

            if (detectedEncoding != null) {
                // Проверяем, поддерживается ли кодировка
                if (Charset.isSupported(detectedEncoding)) {
                    return detectedEncoding;
                }
            }
        }

        // Если автоматическое определение не сработало, пробуем стандартные кодировки
        String[] encodingsToTry = {"UTF-8", "Windows-1251", "ISO-8859-1"};

        for (String encoding : encodingsToTry) {
            try {
                List<String> lines = Files.readAllLines(filePath, Charset.forName(encoding));
                if (!lines.isEmpty()) {
                    // Проверяем, нет ли битых символов
                    String firstLine = lines.get(0);
                    if (!firstLine.contains("�")) { // Символ замещения для битых символов
                        return encoding;
                    }
                }
            } catch (Exception e) {
                // Игнорируем ошибки и пробуем следующую кодировку
            }
        }

        // По умолчанию возвращаем UTF-8
        return StandardCharsets.UTF_8.name();
    }

    /**
     * Читает образец строк из файла
     */
    private List<String> readSampleLines(Path filePath, String encoding) throws IOException {
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath, Charset.forName(encoding))) {
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null && count < MAX_LINES_TO_ANALYZE) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                    count++;
                }
            }
        }

        return lines;
    }

    /**
     * Определяет наиболее вероятный разделитель
     */
    private char detectDelimiter(List<String> sampleLines) {
        Map<Character, Integer> delimiterCounts = new HashMap<>();

        // Подсчитываем встречаемость каждого возможного разделителя
        for (char delimiter : POSSIBLE_DELIMITERS) {
            int totalCount = 0;
            int consistentLines = 0;
            Integer expectedCount = null;

            for (String line : sampleLines) {
                int count = countOccurrences(line, delimiter);
                totalCount += count;

                if (expectedCount == null) {
                    expectedCount = count;
                    consistentLines = 1;
                } else if (expectedCount.equals(count)) {
                    consistentLines++;
                }
            }

            // Учитываем как общее количество, так и консистентность
            int score = totalCount * 10 + consistentLines;
            delimiterCounts.put(delimiter, score);
        }

        // Возвращаем разделитель с наивысшим счетом
        return delimiterCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(','); // По умолчанию запятая
    }

    /**
     * Определяет символ кавычек
     */
    private char detectQuoteChar(List<String> sampleLines, char delimiter) {
        for (char quote : POSSIBLE_QUOTES) {
            boolean hasQuotedFields = sampleLines.stream()
                    .anyMatch(line -> hasQuotedContent(line, delimiter, quote));

            if (hasQuotedFields) {
                return quote;
            }
        }

        return '"'; // По умолчанию двойные кавычки
    }

    /**
     * Определяет символ экранирования
     */
    private char detectEscapeChar(List<String> sampleLines, char quoteChar) {
        // Ищем удвоенные кавычки (стандартный способ экранирования в CSV)
        String doubleQuote = String.valueOf(quoteChar) + quoteChar;

        for (String line : sampleLines) {
            if (line.contains(doubleQuote)) {
                return quoteChar; // Экранирование удвоением
            }
        }

        // Ищем обратный слеш
        for (String line : sampleLines) {
            if (line.contains("\\" + quoteChar)) {
                return '\\';
            }
        }

        return quoteChar; // По умолчанию удвоение кавычек
    }

    /**
     * Парсит заголовки из первой строки
     */
    private List<String> parseHeaders(String headerLine, char delimiter, char quoteChar, char escapeChar) {
        // Простой парсинг с учетом кавычек
        List<String> headers = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < headerLine.length(); i++) {
            char c = headerLine.charAt(i);

            if (escaped) {
                currentField.append(c);
                escaped = false;
            } else if (c == escapeChar && escapeChar != quoteChar) {
                escaped = true;
            } else if (c == quoteChar) {
                if (inQuotes && i + 1 < headerLine.length() && headerLine.charAt(i + 1) == quoteChar) {
                    // Удвоенная кавычка - добавляем одну
                    currentField.append(quoteChar);
                    i++; // Пропускаем следующую кавычку
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                headers.add(currentField.toString().trim());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }

        // Добавляем последнее поле
        headers.add(currentField.toString().trim());

        return headers;
    }

    /**
     * Подсчитывает примерное количество строк в файле
     */
    private long estimateLineCount(Path filePath, String encoding) throws IOException {
        long fileSize = Files.size(filePath);

        // Читаем образец для оценки средней длины строки
        List<String> sampleLines = readSampleLines(filePath, encoding);
        if (sampleLines.isEmpty()) {
            return 0;
        }

        double avgLineLength = sampleLines.stream()
                .mapToInt(line -> line.getBytes(Charset.forName(encoding)).length)
                .average()
                .orElse(100); // Значение по умолчанию

        // Добавляем байты на символы перевода строки
        avgLineLength += 2; // \r\n

        return Math.round(fileSize / avgLineLength);
    }

    /**
     * Подсчитывает количество вхождений символа в строке
     */
    private int countOccurrences(String text, char ch) {
        return (int) text.chars().filter(c -> c == ch).count();
    }

    /**
     * Проверяет, содержит ли строка поля в кавычках
     */
    private boolean hasQuotedContent(String line, char delimiter, char quote) {
        String quoteStr = String.valueOf(quote);

        // Простая проверка: ищем поля, начинающиеся и заканчивающиеся кавычками
        String[] parts = line.split(String.valueOf(delimiter));

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 &&
                    trimmed.startsWith(quoteStr) &&
                    trimmed.endsWith(quoteStr)) {
                return true;
            }
        }

        return false;
    }
}