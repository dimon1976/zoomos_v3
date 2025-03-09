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

        // Проверяем, есть ли значение в списке TRUE_VALUES
        if (TRUE_VALUES.contains(normalizedValue)) {
            return Boolean.TRUE;
        }

        // Проверяем, есть ли значение в списке FALSE_VALUES
        if (FALSE_VALUES.contains(normalizedValue)) {
            return Boolean.FALSE;
        }

        // Если значение не распознано, пытаемся использовать стандартный метод
        try {
            return Boolean.valueOf(normalizedValue);
        } catch (Exception e) {
            // Возвращаем null, если не удалось преобразовать
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

        // Проверяем, есть ли указание формата в параметрах
        if (params != null && !params.isEmpty()) {
            // Формат: "true=Да|false=Нет"
            String[] parts = params.split("\\|");
            for (String part : parts) {
                if (value && part.startsWith("true=")) {
                    return part.substring("true=".length());
                } else if (!value && part.startsWith("false=")) {
                    return part.substring("false=".length());
                }
            }
        }

        // По умолчанию возвращаем стандартный текст
        return value ? "true" : "false";
    }
}