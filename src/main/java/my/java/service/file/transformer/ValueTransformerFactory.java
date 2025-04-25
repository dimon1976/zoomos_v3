// src/main/java/my/java/service/file/transformer/ValueTransformerFactory.java
package my.java.service.file.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Фабрика для трансформаторов значений
 */
@Component
@Slf4j
public class ValueTransformerFactory {

    private final Map<Class<?>, ValueTransformer<?>> transformers = new HashMap<>();
    private final ValueTransformer<String> defaultTransformer;

    @Autowired
    public ValueTransformerFactory(List<ValueTransformer<?>> transformerList) {
        // Регистрируем все трансформаторы
        transformerList.forEach(transformer ->
                transformers.put(transformer.getTargetType(), transformer));

        // Устанавливаем строковый трансформатор по умолчанию
        defaultTransformer = transformers.containsKey(String.class)
                ? (ValueTransformer<String>) transformers.get(String.class)
                : new StringTransformer();
    }

    @SuppressWarnings("unchecked")
    public <T> ValueTransformer<T> getTransformer(Class<T> targetType) {
        if (targetType == null) {
            return (ValueTransformer<T>) defaultTransformer;
        }

        // Для перечислений используем общий трансформатор
        if (targetType.isEnum()) {
            return (ValueTransformer<T>) transformers.getOrDefault(Enum.class, defaultTransformer);
        }

        // Для примитивных типов получаем их обертки
        if (targetType.isPrimitive()) {
            targetType = (Class<T>) mapPrimitiveToWrapper(targetType);
        }

        return (ValueTransformer<T>) transformers.getOrDefault(targetType, defaultTransformer);
    }

    @SuppressWarnings("unchecked")
    public <T> T transform(String value, Class<T> targetType, String params) {
        // Для null или пустых значений
        if (value == null || value.trim().isEmpty()) {
            // Для примитивных типов нельзя вернуть null
            if (targetType.isPrimitive()) {
                return getDefaultPrimitiveValue(targetType);
            }
            return null;
        }

        // Для примитивных типов используем их обертки
        Class<?> effectiveType = targetType.isPrimitive()
                ? mapPrimitiveToWrapper(targetType)
                : targetType;

        // Для перечислений дополняем параметры
        if (effectiveType.isEnum()) {
            String enumParams = "class=" + effectiveType.getName();
            if (params != null && !params.isEmpty()) {
                enumParams += "|" + params;
            }
            return (T) getTransformer(Enum.class).transform(value, enumParams);
        }

        // Используем соответствующий трансформатор
        ValueTransformer<T> transformer = (ValueTransformer<T>) getTransformer(effectiveType);
        return transformer.transform(value, params);
    }

    public <T> boolean canTransform(String value, Class<T> targetType, String params) {
        ValueTransformer<T> transformer = getTransformer(targetType);
        return transformer.canTransform(value, params);
    }

    @SuppressWarnings("unchecked")
    public <T> String toString(T value, String params) {
        if (value == null) return "";

        Class<?> valueType = value.getClass();
        ValueTransformer<T> transformer = (ValueTransformer<T>) getTransformer(valueType);
        return transformer.toString(value, params);
    }

    /**
     * Сопоставляет примитивные типы с их обертками
     */
    private Class<?> mapPrimitiveToWrapper(Class<?> primitiveType) {
        if (int.class.equals(primitiveType)) return Integer.class;
        if (long.class.equals(primitiveType)) return Long.class;
        if (double.class.equals(primitiveType)) return Double.class;
        if (float.class.equals(primitiveType)) return Float.class;
        if (boolean.class.equals(primitiveType)) return Boolean.class;
        if (char.class.equals(primitiveType)) return Character.class;
        if (byte.class.equals(primitiveType)) return Byte.class;
        if (short.class.equals(primitiveType)) return Short.class;
        return primitiveType;
    }

    /**
     * Возвращает значение по умолчанию для примитивных типов
     */
    @SuppressWarnings("unchecked")
    private <T> T getDefaultPrimitiveValue(Class<T> primitiveType) {
        if (int.class.equals(primitiveType)) return (T) Integer.valueOf(0);
        if (long.class.equals(primitiveType)) return (T) Long.valueOf(0L);
        if (double.class.equals(primitiveType)) return (T) Double.valueOf(0.0);
        if (float.class.equals(primitiveType)) return (T) Float.valueOf(0.0f);
        if (boolean.class.equals(primitiveType)) return (T) Boolean.FALSE;
        if (char.class.equals(primitiveType)) return (T) Character.valueOf('\0');
        if (byte.class.equals(primitiveType)) return (T) Byte.valueOf((byte) 0);
        if (short.class.equals(primitiveType)) return (T) Short.valueOf((short) 0);
        return null;
    }
}