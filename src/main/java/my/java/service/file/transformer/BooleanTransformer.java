// src/main/java/my/java/service/file/transformer/BooleanTransformer.java
package my.java.service.file.transformer;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Трансформатор для булевых значений
 */
@Component
public class BooleanTransformer extends AbstractValueTransformer<Boolean> {

    private static final Set<String> TRUE_VALUES = Set.of(
            "true", "yes", "y", "1", "да", "д", "истина", "вкл", "on", "включено"
    );

    private static final Set<String> FALSE_VALUES = Set.of(
            "false", "no", "n", "0", "нет", "н", "ложь", "выкл", "off", "выключено"
    );

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

        try {
            return Boolean.valueOf(normalizedValue);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected Boolean handleEmpty(String params) {
        String defaultValue = getDefaultValue(params, null);
        return defaultValue != null ? transform(defaultValue, null) : null;
    }

    @Override
    public String toString(Boolean value, String params) {
        if (value == null) {
            return "";
        }

        // Проверяем формат в параметрах
        if (params != null && !params.isEmpty()) {
            for (String part : params.split("\\|")) {
                if (value && part.startsWith("true=")) {
                    return part.substring("true=".length());
                } else if (!value && part.startsWith("false=")) {
                    return part.substring("false=".length());
                }
            }
        }

        return value ? "true" : "false";
    }
}