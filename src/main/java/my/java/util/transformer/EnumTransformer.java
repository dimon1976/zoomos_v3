package my.java.util.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Трансформатор для значений перечислений
 */
@Component
@Slf4j
public class EnumTransformer extends AbstractValueTransformer<Enum<?>> {

    /**
     * Конструктор трансформатора перечислений
     */
    @SuppressWarnings("unchecked")
    public EnumTransformer() {
        super((Class<Enum<?>>) (Class<?>) Enum.class);
    }

    @Override
    public Enum<?> transform(String value, String params) {
        if (isEmpty(value)) {
            return handleEmpty(params);
        }

        String enumClassName = extractParameter(params, "class", null);
        if (enumClassName == null) {
            log.warn("Enum class not specified in params: {}", params);
            return null;
        }

        try {
            Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) Class.forName(enumClassName);
            String mapping = extractParameter(params, "mapping", null);

            if (mapping != null) {
                Map<String, String> mappings = parseMappings(mapping);
                String enumValue = mappings.get(value.trim().toLowerCase());
                if (enumValue != null) {
                    return getEnumValue(enumClass, enumValue);
                }
            }

            return getEnumValue(enumClass, value.trim());
        } catch (ClassNotFoundException e) {
            log.warn("Enum class not found: {}", enumClassName, e);
            return null;
        } catch (Exception e) {
            log.warn("Error transforming string to enum: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    protected Enum<?> handleEmpty(String params) {
        return null;
    }

    @Override
    public String toString(Enum<?> value, String params) {
        if (value == null) {
            return "";
        }

        String mapping = extractParameter(params, "mapping", null);
        if (mapping != null) {
            Map<String, String> mappings = parseMappings(mapping);
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (entry.getValue().equals(value.name())) {
                    return entry.getKey();
                }
            }
        }

        return value.name();
    }

    protected String extractParameter(String params, String paramName, String defaultValue) {
        if (params == null) {
            return defaultValue;
        }

        String[] parts = params.split("\\|");
        for (String part : parts) {
            if (part.startsWith(paramName + "=")) {
                return part.substring((paramName + "=").length());
            }
        }

        return defaultValue;
    }

    protected Map<String, String> parseMappings(String mapping) {
        Map<String, String> result = new HashMap<>();

        if (mapping == null || mapping.isEmpty()) {
            return result;
        }

        String[] pairs = mapping.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                result.put(keyValue[0].trim().toLowerCase(), keyValue[1].trim());
            }
        }

        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <E extends Enum<E>> Enum<?> getEnumValue(Class<? extends Enum<?>> enumClass, String value) {
        try {
            return Enum.valueOf((Class<E>) enumClass, value);
        } catch (IllegalArgumentException e) {
            for (Enum<?> enumValue : enumClass.getEnumConstants()) {
                if (enumValue.name().equalsIgnoreCase(value)) {
                    return enumValue;
                }
            }
            throw e;
        }
    }
}