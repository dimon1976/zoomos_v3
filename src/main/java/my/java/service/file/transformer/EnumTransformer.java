// src/main/java/my/java/service/file/transformer/EnumTransformer.java
package my.java.service.file.transformer;

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

    @SuppressWarnings("unchecked")
    public EnumTransformer() {
        super((Class<Enum<?>>) (Class<?>) Enum.class);
    }

    @Override
    public Enum<?> transform(String value, String params) {
        if (isEmpty(value)) {
            return null;
        }

        String enumClassName = extractParameter(params, "class", null);
        if (enumClassName == null) {
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
            return null;
        } catch (Exception e) {
            return null;
        }
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

        for (String part : params.split("\\|")) {
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

        for (String pair : mapping.split(",")) {
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
            // Пробуем поиск без учета регистра
            for (Enum<?> enumValue : enumClass.getEnumConstants()) {
                if (enumValue.name().equalsIgnoreCase(value)) {
                    return enumValue;
                }
            }
            throw e;
        }
    }
}