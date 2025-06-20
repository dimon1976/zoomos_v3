package my.java.util.transformer;

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

        // Параметры могут содержать указание на обрезку пробелов
        boolean trim = true;
        if (params != null && params.contains("trim=false")) {
            trim = false;
        }

        return trim ? value.trim() : value;
    }

    @Override
    protected String handleEmpty(String params) {
        return getDefaultValue(params, "");
    }

    @Override
    public String toString(String value, String params) {
        return value;
    }
}