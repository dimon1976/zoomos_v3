package my.java.service.file.detector;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для автоматического определения параметров CSV файла
 */
@Service
@Slf4j
public class CsvParameterDetector {

    private static final int DETECTION_BYTES = 1_048_576;
    private static final int DETECTION_LINES = 10;
    private static final char[] COMMON_DELIMITERS = {',', ';', '\t', '|'};
    private static final char[] COMMON_QUOTES = {'"', '\''};

    @Data
    @Builder
    public static class CsvParameters {
        private Charset encoding;
        private char delimiter;
        private char quoteChar;
        private boolean hasHeader;
        private int columnCount;
        private List<String> sampleHeaders;
    }

    /**
     * Определяет параметры CSV файла
     */
    public CsvParameters detect(InputStream inputStream) throws IOException {
        log.info("Начало анализа CSV-файла");

        BufferedInputStream bis = new BufferedInputStream(inputStream);
        bis.mark(DETECTION_BYTES * 2); // Увеличить при необходимости (например, до 10_000_000)

        // 1. Определяем кодировку
        Charset encoding;
        try {
            encoding = detectEncoding(bis);
            log.info("Определена кодировка: {}", encoding);
        } catch (Exception e) {
            log.error("Ошибка при определении кодировки", e);
            throw e;
        }

        try {
            bis.reset(); // только один reset
            log.info("bis.reset() после определения кодировки прошёл успешно");
        } catch (IOException e) {
            log.error("Ошибка при reset(): возможно, прочитано больше байт, чем выделено в mark()", e);
            throw e;
        }

        // 2. Читаем и храним sampleLines в память (но не весь файл!)
        List<String> sampleLines;
        try (
                InputStreamReader reader = new InputStreamReader(bis, encoding);
                BufferedReader bufferedReader = new BufferedReader(reader)
        ) {
            sampleLines = new ArrayList<>();
            String line;
            int linesToRead = 10; // читаем только первые N строк
            while ((line = bufferedReader.readLine()) != null && sampleLines.size() < linesToRead) {
                sampleLines.add(line);
            }
            log.info("Прочитано sampleLines: {} строк", sampleLines.size());
        } catch (Exception e) {
            log.error("Ошибка при чтении sampleLines", e);
            throw e;
        }

        // Далее анализ по этим строкам
        char delimiter = detectDelimiter(sampleLines);
        char quoteChar = detectQuoteChar(sampleLines, delimiter);
        boolean hasHeader = detectHeader(sampleLines, delimiter, quoteChar);
        int columnCount = countColumns(sampleLines.get(0), delimiter, quoteChar);
        List<String> sampleHeaders = hasHeader ?
                parseRow(sampleLines.get(0), delimiter, quoteChar) :
                generateDefaultHeaders(columnCount);

        return CsvParameters.builder()
                .encoding(encoding)
                .delimiter(delimiter)
                .quoteChar(quoteChar)
                .hasHeader(hasHeader)
                .columnCount(columnCount)
                .sampleHeaders(sampleHeaders)
                .build();
    }

    /**
     * Определяет кодировку файла
     */
    private Charset detectEncoding(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        UniversalDetector detector = new UniversalDetector(null);

        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) > 0 && !detector.isDone()) {
            detector.handleData(buffer, 0, bytesRead);
        }

        detector.dataEnd();

        String encoding = detector.getDetectedCharset();
        detector.reset();

        if (encoding != null) {
            try {
                return Charset.forName(encoding);
            } catch (IllegalArgumentException e) {
                log.warn("Определённая кодировка не поддерживается: {}", encoding, e);
            }
        }

        log.info("Кодировка не определена. Используется по умолчанию UTF-8");
        return StandardCharsets.UTF_8;
    }

    /**
     * Читает первые строки файла для анализа
     */
    private List<String> readSampleLines(InputStream is, Charset charset) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < DETECTION_LINES) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                    count++;
                }
            }
        }
        return lines;
    }

    /**
     * Определяет разделитель
     */
    private char detectDelimiter(List<String> lines) {
        Map<Character, Integer> delimiterScores = new HashMap<>();

        for (char delimiter : COMMON_DELIMITERS) {
            int score = 0;
            int prevCount = -1;

            for (String line : lines) {
                int count = countOccurrences(line, delimiter);
                if (count > 0) {
                    score += count;
                    // Бонус за постоянное количество разделителей
                    if (prevCount == count) {
                        score += 10;
                    }
                    prevCount = count;
                }
            }

            delimiterScores.put(delimiter, score);
        }

        return delimiterScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(',');
    }

    /**
     * Определяет символ экранирования
     */
    private char detectQuoteChar(List<String> lines, char delimiter) {
        Map<Character, Integer> quoteScores = new HashMap<>();

        for (char quote : COMMON_QUOTES) {
            int score = 0;
            for (String line : lines) {
                // Проверяем парные кавычки
                int count = countOccurrences(line, quote);
                if (count > 0 && count % 2 == 0) {
                    score += count;
                    // Бонус если кавычки окружают разделители
                    if (line.contains(quote + String.valueOf(delimiter) + quote)) {
                        score += 5;
                    }
                }
            }
            quoteScores.put(quote, score);
        }

        return quoteScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse('"');
    }

    /**
     * Определяет наличие заголовков
     */
    private boolean detectHeader(List<String> lines, char delimiter, char quoteChar) {
        if (lines.size() < 2) return false;

        List<String> firstRow = parseRow(lines.get(0), delimiter, quoteChar);
        List<String> secondRow = parseRow(lines.get(1), delimiter, quoteChar);

        // Эвристики для определения заголовков
        int headerScore = 0;

        for (int i = 0; i < Math.min(firstRow.size(), secondRow.size()); i++) {
            String first = firstRow.get(i);
            String second = secondRow.get(i);

            // Заголовки обычно не числа
            if (!isNumeric(first) && isNumeric(second)) {
                headerScore += 2;
            }

            // Заголовки обычно короче данных
            if (first.length() < second.length()) {
                headerScore++;
            }

            // Заголовки часто содержат подчеркивания или CamelCase
            if (first.contains("_") || first.contains(" ") || isCamelCase(first)) {
                headerScore++;
            }
        }

        return headerScore >= firstRow.size() / 2;
    }

    /**
     * Парсит строку CSV
     */
    private List<String> parseRow(String line, char delimiter, char quoteChar) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == quoteChar) {
                inQuotes = !inQuotes;
            } else if (ch == delimiter && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }

        result.add(current.toString().trim());
        return result;
    }

    /**
     * Подсчитывает количество колонок
     */
    private int countColumns(String line, char delimiter, char quoteChar) {
        return parseRow(line, delimiter, quoteChar).size();
    }

    /**
     * Генерирует заголовки по умолчанию
     */
    private List<String> generateDefaultHeaders(int count) {
        List<String> headers = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            headers.add("Колонка_" + i);
        }
        return headers;
    }

    /**
     * Утилиты
     */
    private int countOccurrences(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str.replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isCamelCase(String str) {
        return str.length() > 1 &&
                Character.isLowerCase(str.charAt(0)) &&
                str.chars().anyMatch(Character::isUpperCase);
    }
}