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

/**
 * Класс для хранения параметров чтения и обработки файлов.
 * Объединяет параметры для различных типов файлов (CSV, Excel и др.) и предоставляет
 * типизированный доступ к ним.
 * <p>
 * Преимущества использования этого класса вместо Map<String, String>:
 * <ul>
 *     <li>Типовая безопасность (ошибки выявляются на этапе компиляции)</li>
 *     <li>Самодокументируемый код (поля класса ясно показывают доступные параметры)</li>
 *     <li>Удобный доступ через геттеры/сеттеры вместо работы со строковыми ключами</li>
 *     <li>Возможность валидации значений параметров</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileReadingOptions {
    // Общие параметры для всех типов файлов
    /**
     * Номер строки с заголовками (начиная с 0).
     */
    private int headerRow = 0;

    /**
     * Номер строки, с которой начинаются данные (обычно headerRow + 1).
     */
    private int dataStartRow = 1;

    /**
     * Пропускать пустые строки при обработке.
     */
    private boolean skipEmptyRows = true;

    /**
     * Удалять лишние пробелы в начале и конце значений.
     */
    private boolean trimWhitespace = true;

    /**
     * Выполнять валидацию данных перед импортом.
     */
    private boolean validateData = true;

    /**
     * Формат даты по умолчанию для преобразования строк в даты.
     */
    private String dateFormat = "dd.MM.yyyy";

    // Параметры обработки данных
    /**
     * Определяет поведение при ошибках обработки:
     * <ul>
     *     <li>continue - продолжать обработку, пропуская ошибочные записи</li>
     *     <li>stop - остановить обработку при первой ошибке</li>
     *     <li>report - собирать отчет об ошибках и продолжать</li>
     * </ul>
     */
    private String errorHandling = "continue";

    /**
     * Определяет поведение при обнаружении дубликатов:
     * <ul>
     *     <li>skip - пропускать дубликаты</li>
     *     <li>update - обновлять существующие записи</li>
     *     <li>error - выдавать ошибку при дубликатах</li>
     * </ul>
     */
    private String duplicateHandling = "skip";

    /**
     * Стратегия обработки данных:
     * <ul>
     *     <li>insert - только вставка новых записей</li>
     *     <li>update - обновление существующих записей</li>
     *     <li>upsert - вставка новых и обновление существующих</li>
     *     <li>replace - замена всех существующих записей</li>
     * </ul>
     */
    private String processingStrategy = "insert";

    /**
     * Размер пакета при массовой вставке/обновлении данных.
     */
    private int batchSize = 500;

    // Параметры для CSV
    /**
     * Разделитель полей в CSV-файле.
     */
    private Character delimiter = ',';

    /**
     * Символ обрамления текста в CSV-файле.
     */
    private Character quoteChar = '"';

    /**
     * Кодировка файла.
     */
    private Charset charset = StandardCharsets.UTF_8;

    /**
     * Символ экранирования в CSV-файле.
     */
    private Character escapeChar = '\\';

    /**
     * Признак наличия заголовков в файле.
     */
    private boolean hasHeader = true;

    // Параметры для Excel
    /**
     * Имя листа в Excel-файле.
     */
    private String sheetName;

    /**
     * Индекс листа в Excel-файле (начиная с 0).
     */
    private Integer sheetIndex = 0;

    /**
     * Вычислять формулы в Excel-файле.
     */
    private boolean evaluateFormulas = true;

    // Параметры обработки пустых значений
    /**
     * Определяет поведение при обнаружении пустых ячеек:
     * <ul>
     *     <li>empty - оставлять пустыми</li>
     *     <li>null - установить NULL</li>
     *     <li>default - использовать значения по умолчанию</li>
     * </ul>
     */
    private String emptyFieldHandling = "empty";

    // Формат-специфичные параметры хранятся в Map
    /**
     * Дополнительные параметры, которые не имеют типизированных полей.
     */
    private Map<String, String> additionalParams = new HashMap<>();

    /**
     * Создает экземпляр FileReadingOptions из Map параметров
     *
     * @param params карта параметров
     * @return объект FileReadingOptions
     */
    public static FileReadingOptions fromMap(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return new FileReadingOptions();
        }

        FileReadingOptions options = new FileReadingOptions();

        // Общие параметры
        options.setHeaderRow(getIntParam(params, "headerRow", 0));
        options.setDataStartRow(getIntParam(params, "dataStartRow", 1));
        options.setSkipEmptyRows(getBooleanParam(params, "skipEmptyRows", true));
        options.setTrimWhitespace(getBooleanParam(params, "trimWhitespace", true));
        options.setValidateData(getBooleanParam(params, "validateData", true));
        options.setDateFormat(getStringParam(params, "dateFormat", "dd.MM.yyyy"));

        // Параметры обработки данных
        options.setErrorHandling(getStringParam(params, "errorHandling", "continue"));
        options.setDuplicateHandling(getStringParam(params, "duplicateHandling", "skip"));
        options.setProcessingStrategy(getStringParam(params, "processingStrategy", "insert"));
        options.setBatchSize(getIntParam(params, "batchSize", 500));

        // Параметры для CSV
        String delimiterStr = getStringParam(params, "delimiter", ",");
        if (delimiterStr != null && !delimiterStr.isEmpty()) {
            options.setDelimiter(delimiterStr.charAt(0));
        }

        String quoteCharStr = getStringParam(params, "quoteChar", "\"");
        if (quoteCharStr != null && !quoteCharStr.isEmpty()) {
            options.setQuoteChar(quoteCharStr.charAt(0));
        }

        String charsetStr = getStringParam(params, "charset", "UTF-8");
        if (charsetStr != null && !charsetStr.isEmpty()) {
            try {
                options.setCharset(Charset.forName(charsetStr));
            } catch (Exception e) {
                // При ошибке используем UTF-8
            }
        }

        String escapeCharStr = getStringParam(params, "escapeChar", "\\");
        if (escapeCharStr != null && !escapeCharStr.isEmpty()) {
            options.setEscapeChar(escapeCharStr.charAt(0));
        }

        options.setHasHeader(getBooleanParam(params, "hasHeader", true));

        // Параметры для Excel
        options.setSheetName(getStringParam(params, "sheetName", null));
        options.setSheetIndex(getIntParam(params, "sheetIndex", 0));
        options.setEvaluateFormulas(getBooleanParam(params, "evaluateFormulas", true));

        // Параметры обработки пустых значений
        options.setEmptyFieldHandling(getStringParam(params, "emptyFieldHandling", "empty"));

        // Сохраняем все дополнительные параметры
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!isStandardParam(entry.getKey())) {
                options.getAdditionalParams().put(entry.getKey(), entry.getValue());
            }
        }

        return options;
    }


    /**
     * Преобразует объект FileReadingOptions обратно в Map<String, String>.
     * Это полезно для обратной совместимости.
     *
     * @return карта параметров
     */
    public Map<String, String> toMap() {
        Map<String, String> params = new HashMap<>();

        // Общие параметры
        params.put("headerRow", String.valueOf(headerRow));
        params.put("dataStartRow", String.valueOf(dataStartRow));
        params.put("skipEmptyRows", String.valueOf(skipEmptyRows));
        params.put("trimWhitespace", String.valueOf(trimWhitespace));
        params.put("validateData", String.valueOf(validateData));
        params.put("dateFormat", dateFormat);

        // Параметры обработки данных
        params.put("errorHandling", errorHandling);
        params.put("duplicateHandling", duplicateHandling);
        params.put("processingStrategy", processingStrategy);
        params.put("batchSize", String.valueOf(batchSize));

        // Параметры для CSV
        params.put("delimiter", String.valueOf(delimiter));
        params.put("quoteChar", String.valueOf(quoteChar));
        params.put("charset", charset.name());
        params.put("escapeChar", String.valueOf(escapeChar));
        params.put("hasHeader", String.valueOf(hasHeader));

        // Параметры для Excel
        if (sheetName != null) {
            params.put("sheetName", sheetName);
        }
        params.put("sheetIndex", String.valueOf(sheetIndex));
        params.put("evaluateFormulas", String.valueOf(evaluateFormulas));

        // Параметры обработки пустых значений
        params.put("emptyFieldHandling", emptyFieldHandling);

        // Добавляем все дополнительные параметры
        params.putAll(additionalParams);

        return params;
    }

    /**
     * Обновляет стратегию обработки ошибок на основе записей в БД или других параметров.
     *
     * @param entityType тип сущности
     * @param clientId идентификатор клиента
     * @return this для цепочки вызовов
     */
    public FileReadingOptions updateStrategy(String entityType, Long clientId) {
        // Пример обновления стратегии
        // В будущем здесь может быть логика выбора оптимальной стратегии
        // на основе типа сущности, клиента и других параметров

        String entityTypeStr = getAdditionalParam("entityType", entityType);
        if (entityTypeStr.equals("product")) {
            // Для продуктов используем стратегию upsert по умолчанию
            if (processingStrategy.equals("insert")) {
                processingStrategy = "upsert";
            }
        }

        // Обновляем параметры в зависимости от размера данных
        Integer totalRecords = getIntAdditionalParam("totalRecords", 0);
        if (totalRecords > 1000) {
            // Для больших наборов данных увеличиваем размер пакета
            batchSize = Math.max(batchSize, 1000);
        }

        return this;
    }

    /**
     * Получает целочисленный дополнительный параметр.
     *
     * @param key ключ параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра
     */
    public Integer getIntAdditionalParam(String key, Integer defaultValue) {
        String value = additionalParams.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Получает целочисленный параметр из Map
     */
    private static int getIntParam(Map<String, String> params, String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(params.get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Получает логический параметр из Map
     */
    private static boolean getBooleanParam(Map<String, String> params, String key, boolean defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }

        String value = params.get(key);
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * Получает строковый параметр из Map
     */
    private static String getStringParam(Map<String, String> params, String key, String defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }

        return params.get(key);
    }

    /**
     * Проверяет, является ли параметр стандартным
     */
    private static boolean isStandardParam(String key) {
        // Список всех стандартных параметров
        return key.equals("headerRow") ||
                key.equals("dataStartRow") ||
                key.equals("skipEmptyRows") ||
                key.equals("trimWhitespace") ||
                key.equals("validateData") ||
                key.equals("dateFormat") ||
                key.equals("errorHandling") ||
                key.equals("duplicateHandling") ||
                key.equals("processingStrategy") ||
                key.equals("batchSize") ||
                key.equals("delimiter") ||
                key.equals("quoteChar") ||
                key.equals("charset") ||
                key.equals("escapeChar") ||
                key.equals("hasHeader") ||
                key.equals("sheetName") ||
                key.equals("sheetIndex") ||
                key.equals("evaluateFormulas") ||
                key.equals("emptyFieldHandling");
    }

    /**
     * Получает дополнительный параметр
     */
    public String getAdditionalParam(String key, String defaultValue) {
        return additionalParams.getOrDefault(key, defaultValue);
    }

    /**
     * Валидирует параметры импорта и корректирует значения при необходимости.
     *
     * @return this для цепочки вызовов
     */
    public FileReadingOptions validate() {
        // Проверяем и корректируем параметры

        // Проверка индекса строки заголовка
        if (headerRow < 0) {
            headerRow = 0;
        }

        // Проверка строки начала данных
        if (dataStartRow <= headerRow) {
            dataStartRow = headerRow + 1;
        }

        // Проверка размера пакета
        if (batchSize < 1) {
            batchSize = 500;
        } else if (batchSize > 10000) {
            batchSize = 10000;
        }

        // Проверка стратегии обработки ошибок
        if (!Arrays.asList("continue", "stop", "report").contains(errorHandling)) {
            errorHandling = "continue";
        }

        // Проверка стратегии обработки дубликатов
        if (!Arrays.asList("skip", "update", "error").contains(duplicateHandling)) {
            duplicateHandling = "skip";
        }

        // Проверка стратегии обработки
        if (!Arrays.asList("insert", "update", "upsert", "replace").contains(processingStrategy)) {
            processingStrategy = "insert";
        }

        // Проверка обработки пустых значений
        if (!Arrays.asList("empty", "null", "default").contains(emptyFieldHandling)) {
            emptyFieldHandling = "empty";
        }

        return this;
    }

    /**
     * Проверяет, соответствуют ли параметры требованиям для обработки файла.
     *
     * @return true, если параметры валидны
     */
    public boolean isValid() {
        // Базовая валидация параметров
        return batchSize > 0 &&
                Arrays.asList("continue", "stop", "report").contains(errorHandling) &&
                Arrays.asList("skip", "update", "error").contains(duplicateHandling) &&
                Arrays.asList("insert", "update", "upsert", "replace").contains(processingStrategy) &&
                Arrays.asList("empty", "null", "default").contains(emptyFieldHandling);
    }

    /**
     * Логирует все текущие настройки на уровне DEBUG.
     *
     * @param logger логгер для вывода информации
     * @return this для цепочки вызовов
     */
    public FileReadingOptions logSettings(org.slf4j.Logger logger) {
        if (logger.isDebugEnabled()) {
            logger.debug("Настройки импорта:");
            logger.debug("- headerRow: {}", headerRow);
            logger.debug("- dataStartRow: {}", dataStartRow);
            logger.debug("- skipEmptyRows: {}", skipEmptyRows);
            logger.debug("- trimWhitespace: {}", trimWhitespace);
            logger.debug("- validateData: {}", validateData);
            logger.debug("- dateFormat: {}", dateFormat);
            logger.debug("- errorHandling: {}", errorHandling);
            logger.debug("- duplicateHandling: {}", duplicateHandling);
            logger.debug("- processingStrategy: {}", processingStrategy);
            logger.debug("- batchSize: {}", batchSize);
            logger.debug("- delimiter: {}", delimiter);
            logger.debug("- quoteChar: {}", quoteChar);
            logger.debug("- charset: {}", charset);
            logger.debug("- escapeChar: {}", escapeChar);
            logger.debug("- hasHeader: {}", hasHeader);
            logger.debug("- sheetName: {}", sheetName);
            logger.debug("- sheetIndex: {}", sheetIndex);
            logger.debug("- evaluateFormulas: {}", evaluateFormulas);
            logger.debug("- emptyFieldHandling: {}", emptyFieldHandling);

            // Логируем дополнительные параметры
            if (!additionalParams.isEmpty()) {
                logger.debug("Дополнительные параметры:");
                additionalParams.forEach((key, value) -> logger.debug("- {}: {}", key, value));
            }
        }
        return this;
    }

    /**
     * Проверяет наличие дополнительного параметра
     */
    public boolean hasAdditionalParam(String key) {
        return additionalParams.containsKey(key);
    }
}