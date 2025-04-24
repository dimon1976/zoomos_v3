// src/main/java/my/java/service/file/options/FileReadingOptions.java
package my.java.service.file.options;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Класс для хранения параметров чтения и обработки файлов.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileReadingOptions {
    // Общие параметры для всех типов файлов
    private int headerRow = 0;
    private int dataStartRow = 1;
    private boolean skipEmptyRows = true;
    private boolean trimWhitespace = true;
    private boolean validateData = true;
    private String dateFormat = "dd.MM.yyyy";
    private String errorHandling = "continue";
    private String duplicateHandling = "skip";
    private String processingStrategy = "insert";
    private int batchSize = 500;

    // Параметры для CSV
    private Character delimiter;
    private Character quoteChar;
    private Charset charset;
    private Character escapeChar;
    private boolean hasHeader = true;

    // Параметры для Excel
    private String sheetName;
    private Integer sheetIndex = 0;
    private boolean evaluateFormulas = true;

    // Параметры обработки пустых значений
    private String emptyFieldHandling = "empty";

    // Дополнительные параметры
    private Map<String, String> additionalParams = new HashMap<>();

    /**
     * Создает экземпляр FileReadingOptions из Map параметров
     */
    public static FileReadingOptions fromMap(Map<String, String> params) {
        FileReadingOptions options = new FileReadingOptions();
        if (params == null || params.isEmpty()) {
            return options;
        }

        // Создаем новый Map с очищенными ключами
        Map<String, String> cleanedParams = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String cleanedKey = cleanParamKey(entry.getKey());
            cleanedParams.put(cleanedKey, entry.getValue());
        }

        // Теперь работаем с cleanedParams вместо params
        options.headerRow = getIntParam(cleanedParams, "headerRow", 0);
        options.dataStartRow = getIntParam(cleanedParams, "dataStartRow", 1);
        options.skipEmptyRows = getBooleanParam(cleanedParams, "skipEmptyRows", true);
        options.trimWhitespace = getBooleanParam(cleanedParams, "trimWhitespace", true);
        options.validateData = getBooleanParam(cleanedParams, "validateData", true);
        options.dateFormat = getStringParam(cleanedParams, "dateFormat", "dd.MM.yyyy");
        options.errorHandling = getStringParam(cleanedParams, "errorHandling", "continue");
        options.duplicateHandling = getStringParam(cleanedParams, "duplicateHandling", "skip");
        options.processingStrategy = getStringParam(cleanedParams, "processingStrategy", "insert");
        options.batchSize = getIntParam(cleanedParams, "batchSize", 500);

        // CSV параметры
        String delimiterStr = getStringParam(cleanedParams, "delimiter", null);
        if (!isAuto(delimiterStr) && delimiterStr != null && !delimiterStr.isEmpty()) {
            options.delimiter = delimiterStr.charAt(0);
        }

        String quoteCharStr = getStringParam(cleanedParams, "quoteChar", null);
        if (!isAuto(quoteCharStr) && quoteCharStr != null && !quoteCharStr.isEmpty()) {
            options.quoteChar = quoteCharStr.charAt(0);
        }

        String charsetStr = getStringParam(cleanedParams, "encoding", null);
        if (!isAuto(charsetStr) && charsetStr != null && !charsetStr.isEmpty()) {
            try {
                options.charset = Charset.forName(charsetStr);
            } catch (Exception e) {
                // Можно залогировать: некорректная кодировка
            }
        }

        String escapeCharStr = getStringParam(cleanedParams, "escapeChar", null);
        if (!isAuto(escapeCharStr) && escapeCharStr != null && !escapeCharStr.isEmpty()) {
            options.escapeChar = escapeCharStr.charAt(0);
        }

        options.hasHeader = getBooleanParam(cleanedParams, "hasHeader", true);

        // Excel параметры
        options.sheetName = getStringParam(cleanedParams, "sheetName", null);
        options.sheetIndex = getIntParam(cleanedParams, "sheetIndex", 0);
        options.evaluateFormulas = getBooleanParam(cleanedParams, "evaluateFormulas", true);
        options.emptyFieldHandling = getStringParam(cleanedParams, "emptyFieldHandling", "empty");

        // Дополнительные параметры (оставляем оригинальные ключи, если нужно)
        for (Map.Entry<String, String> entry : cleanedParams.entrySet()) {
            if (!isStandardParam(entry.getKey())) {
                options.additionalParams.put(entry.getKey(), entry.getValue());
            }
        }

        return options;
    }

    private static boolean isAuto(String value) {
        return value != null && value.equalsIgnoreCase("auto");
    }



    /**
     * Обновляет стратегию обработки на основе типа сущности и клиента
     */
    public FileReadingOptions updateStrategy(String entityType, Long clientId) {
        String entityTypeStr = getAdditionalParam("entityType", entityType);

        // Оптимизированная логика выбора стратегии
        if (entityTypeStr.equals("product") && processingStrategy.equals("insert")) {
            processingStrategy = "upsert";
        }

        // Оптимизация для больших данных
        Integer totalRecords = getIntAdditionalParam("totalRecords", 0);
        if (totalRecords > 1000) {
            batchSize = Math.max(batchSize, 1000);
        }

        return this;
    }

    /**
     * Очищает ключ параметра от префиксов и суффиксов, например:
     * - "params[emptyFieldHandling]" → "emptyFieldHandling"
     * - "options[delimiter]" → "delimiter"
     */
    private static String cleanParamKey(String key) {
        if (key == null) return null;

        // Удаляем "params[" в начале и "]" в конце, если есть
        if (key.startsWith("params[") && key.endsWith("]")) {
            return key.substring(7, key.length() - 1);
        }
        // Аналогично для других возможных префиксов, например "options["
        if (key.startsWith("options[") && key.endsWith("]")) {
            return key.substring(8, key.length() - 1);
        }
        // Если формат не подходит, возвращаем ключ как есть
        return key;
    }

    // Добавление метода копирования в FileReadingOptions
    public FileReadingOptions copy() {
        return FileReadingOptions.builder()
                .headerRow(headerRow)
                .dataStartRow(dataStartRow)
                .skipEmptyRows(skipEmptyRows)
                .trimWhitespace(trimWhitespace)
                .validateData(validateData)
                .dateFormat(dateFormat)
                .errorHandling(errorHandling)
                .duplicateHandling(duplicateHandling)
                .processingStrategy(processingStrategy)
                .batchSize(batchSize)
                .delimiter(delimiter)
                .quoteChar(quoteChar)
                .charset(charset)
                .escapeChar(escapeChar)
                .hasHeader(hasHeader)
                .sheetName(sheetName)
                .sheetIndex(sheetIndex)
                .evaluateFormulas(evaluateFormulas)
                .emptyFieldHandling(emptyFieldHandling)
                .additionalParams(new HashMap<>(additionalParams))
                .build();
    }

    /**
     * Получает дополнительный целочисленный параметр
     */
    public Integer getIntAdditionalParam(String key, Integer defaultValue) {
        String value = additionalParams.get(key);
        if (value == null) return defaultValue;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Получает дополнительный параметр
     */
    public String getAdditionalParam(String key, String defaultValue) {
        return additionalParams.getOrDefault(key, defaultValue);
    }

    /**
     * Валидирует параметры и корректирует их при необходимости
     */
    public FileReadingOptions validate() {
        // Коррекция параметров
        headerRow = Math.max(0, headerRow);
        dataStartRow = Math.max(headerRow + 1, dataStartRow);
        batchSize = Math.max(1, Math.min(10000, batchSize));

        // Проверка значений перечислений
        errorHandling = validateEnum(errorHandling,
                new String[]{"continue", "stop", "report"}, "continue");

        duplicateHandling = validateEnum(duplicateHandling,
                new String[]{"skip", "update", "error"}, "skip");

        processingStrategy = validateEnum(processingStrategy,
                new String[]{"insert", "update", "upsert", "replace"}, "insert");

        emptyFieldHandling = validateEnum(emptyFieldHandling,
                new String[]{"empty", "null", "default"}, "empty");

        return this;
    }

    /**
     * Проверяет принадлежность значения к списку допустимых
     */
    private String validateEnum(String value, String[] validValues, String defaultValue) {
        for (String validValue : validValues) {
            if (validValue.equals(value)) return value;
        }
        return defaultValue;
    }

    /**
     * Проверяет валидность параметров
     */
    public boolean isValid() {
        return batchSize > 0 &&
                Arrays.asList("continue", "stop", "report").contains(errorHandling) &&
                Arrays.asList("skip", "update", "error").contains(duplicateHandling) &&
                Arrays.asList("insert", "update", "upsert", "replace").contains(processingStrategy) &&
                Arrays.asList("empty", "null", "default").contains(emptyFieldHandling);
    }

    /**
     * Проверяет наличие дополнительного параметра
     */
    public boolean hasAdditionalParam(String key) {
        return additionalParams.containsKey(key);
    }

    // Приватные вспомогательные методы
    private static int getIntParam(Map<String, String> params, String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) return defaultValue;

        try {
            return Integer.parseInt(params.get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBooleanParam(Map<String, String> params, String key, boolean defaultValue) {
        if (params == null || !params.containsKey(key)) return defaultValue;

        String value = params.get(key);
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String getStringParam(Map<String, String> params, String key, String defaultValue) {
        return params == null || !params.containsKey(key) ? defaultValue : params.get(key);
    }

    private static boolean isStandardParam(String key) {
        return Arrays.asList("headerRow", "dataStartRow", "skipEmptyRows", "trimWhitespace",
                "validateData", "dateFormat", "errorHandling", "duplicateHandling",
                "processingStrategy", "batchSize", "delimiter", "quoteChar",
                "charset", "escapeChar", "hasHeader", "sheetName", "sheetIndex",
                "evaluateFormulas", "emptyFieldHandling").contains(key);
    }
}