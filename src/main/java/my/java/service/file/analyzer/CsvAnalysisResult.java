// src/main/java/my/java/service/file/analyzer/CsvAnalysisResult.java
package my.java.service.file.analyzer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Результат анализа CSV файла
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvAnalysisResult {

    /**
     * Определенная кодировка файла
     */
    private String encoding;

    /**
     * Разделитель полей
     */
    private char delimiter;

    /**
     * Символ кавычек
     */
    private char quoteChar;

    /**
     * Символ экранирования
     */
    private char escapeChar;

    /**
     * Заголовки столбцов
     */
    private List<String> headers;

    /**
     * Примерное количество строк в файле
     */
    private long estimatedLines;

    /**
     * Размер файла в байтах
     */
    private long fileSize;

    /**
     * Образец первых строк файла для предварительного просмотра
     */
    private List<String> sampleLines;

    /**
     * Содержит ли файл заголовки в первой строке
     */
    private boolean hasHeaders;

    /**
     * Дополнительные замечания или предупреждения
     */
    private List<String> warnings;

    /**
     * Возвращает разделитель в удобном для отображения формате
     */
    public String getDelimiterDisplay() {
        switch (delimiter) {
            case ',': return "Запятая (,)";
            case ';': return "Точка с запятой (;)";
            case '\t': return "Табуляция";
            case '|': return "Вертикальная черта (|)";
            case ':': return "Двоеточие (:)";
            default: return "Символ '" + delimiter + "'";
        }
    }

    /**
     * Возвращает символ кавычек в удобном для отображения формате
     */
    public String getQuoteCharDisplay() {
        switch (quoteChar) {
            case '"': return "Двойные кавычки (\")";
            case '\'': return "Одинарные кавычки (')";
            default: return "Символ '" + quoteChar + "'";
        }
    }

    /**
     * Возвращает символ экранирования в удобном для отображения формате
     */
    public String getEscapeCharDisplay() {
        if (escapeChar == quoteChar) {
            return "Удвоение символа кавычек";
        }
        switch (escapeChar) {
            case '\\': return "Обратный слеш (\\)";
            default: return "Символ '" + escapeChar + "'";
        }
    }

    /**
     * Возвращает размер файла в удобном для чтения формате
     */
    public String getFileSizeDisplay() {
        if (fileSize < 1024) {
            return fileSize + " байт";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f КБ", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f МБ", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f ГБ", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Возвращает форматированное количество строк
     */
    public String getEstimatedLinesDisplay() {
        if (estimatedLines < 1000) {
            return String.valueOf(estimatedLines);
        } else if (estimatedLines < 1000000) {
            return String.format("%.1f тыс.", estimatedLines / 1000.0);
        } else {
            return String.format("%.1f млн.", estimatedLines / 1000000.0);
        }
    }

    /**
     * Проверяет, является ли файл большим (требующим особой обработки)
     */
    public boolean isLargeFile() {
        return fileSize > 10 * 1024 * 1024 || estimatedLines > 50000; // > 10MB или > 50k строк
    }

    /**
     * Возвращает приоритет обработки на основе размера файла
     */
    public String getProcessingPriority() {
        if (fileSize > 100 * 1024 * 1024 || estimatedLines > 500000) {
            return "НИЗКИЙ"; // Очень большие файлы
        } else if (fileSize > 10 * 1024 * 1024 || estimatedLines > 50000) {
            return "СРЕДНИЙ"; // Большие файлы
        } else {
            return "ВЫСОКИЙ"; // Небольшие файлы
        }
    }
}