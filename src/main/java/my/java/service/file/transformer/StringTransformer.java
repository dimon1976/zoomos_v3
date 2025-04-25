// src/main/java/my/java/service/file/transformer/StringTransformer.java
package my.java.service.file.transformer;

import org.springframework.stereotype.Component;

/**
 * Трансформатор для строковых значений
 */
@Component
public class StringTransformer extends AbstractValueTransformer<String> {

    /**
     * Конструктор трансформатора строк
     */
    public StringTransformer() {
        super(String.class);
    }

    @Override
    public String transform(String value, String params) {
        if (isEmpty(value)) {
            return getDefaultValue(params, "");
        }

        // Обрезаем пробелы, если требуется
        boolean trim = !extractParam(params, "trim", "true").equals("false");
        return trim ? value.trim() : value;
    }

    @Override
    public String toString(String value, String params) {
        return value != null ? value : "";
    }

    /**
     * Извлекает значение параметра
     */
    private String extractParam(String params, String key, String defaultValue) {
        if (params == null || params.isEmpty()) {
            return defaultValue;
        }

        for (String part : params.split("\\|")) {
            if (part.startsWith(key + "=")) {
                return part.substring(key.length() + 1);
            }
        }

        return defaultValue;
    }
}