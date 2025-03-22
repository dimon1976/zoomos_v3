package my.java.service.file.transformer;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Трансформатор для булевых значений
 */
@Component
public class BooleanTransformer extends AbstractValueTransformer<Boolean> {

    private static final Set<String> TRUE_VALUES = new HashSet<>(Arrays.asList(
            "true", "yes", "y", "1", "да", "д", "истина", "вкл", "on", "включено"
    ));

    private static final Set<String> FALSE_VALUES = new HashSet<>(Arrays.asList(
            "false", "no", "n", "0", "нет", "н", "ложь", "выкл", "off", "выключено"
    ));

    /**
     * Конструктор трансформатора булевых значений
     */
    public BooleanTransformer() {
        super(Boolean.class);
    }

    @Override
    public Boolean transform(String value, String params) {
        if (isEmpty(value)) {
            return handleEmpty(params);
        }

        String normalizedValue = value.trim().toLowerCase();

        if (TRUE_VALUES.contains(normalizedValue)) {
            return Boolean.TRUE;
        }

        if (FALSE_VALUES.contains(normalizedValue)) {
            return Boolean.FALSE;
        }

        // Если значение не распознано, пытаемся использовать стандартный метод
        try {
            return Boolean.valueOf(normalizedValue);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected Boolean handleEmpty(String params) {
        String defaultValue = getDefaultValue(params, null);
        if (defaultValue != null) {
            return transform(defaultValue, null);
        }
        return null;
    }

    @Override
    public String toString(Boolean value, String params) {
        if (value == null) {
            return "";
        }

        // Ищем форматы для true/false в параметрах
        String trueFormat = extractParameter(params, "true", "true");
        String falseFormat = extractParameter(params, "false", "false");

        return value ? trueFormat : falseFormat;
    }
}