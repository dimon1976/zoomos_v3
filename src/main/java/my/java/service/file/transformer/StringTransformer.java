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
            return handleEmpty(params);
        }

        // Проверяем параметр обрезки пробелов
        boolean trim = !Boolean.FALSE.toString().equalsIgnoreCase(
                extractParameter(params, "trim", Boolean.TRUE.toString()));

        return trim ? value.trim() : value;
    }

    @Override
    protected String handleEmpty(String params) {
        return getDefaultValue(params, "");
    }

    @Override
    public String toString(String value, String params) {
        return value == null ? "" : value;
    }
}