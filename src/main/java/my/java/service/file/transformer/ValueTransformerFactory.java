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

    /**
     * Конструктор фабрики трансформаторов
     */
    @Autowired
    public ValueTransformerFactory(List<ValueTransformer<?>> transformerList) {
        // Регистрируем все трансформаторы
        transformerList.forEach(transformer ->
                transformers.put(transformer.getTargetType(), transformer));

        // Устанавливаем строковый трансформатор по умолчанию
        if (transformers.containsKey(String.class)) {
            defaultTransformer = (ValueTransformer<String>) transformers.get(String.class);
        } else {
            defaultTransformer = new StringTransformer();
            transformers.put(String.class, defaultTransformer);
        }
    }

    /**
     * Получает трансформатор для указанного типа
     */
    @SuppressWarnings("unchecked")
    public <T> ValueTransformer<T> getTransformer(Class<T> targetType) {
        if (targetType == null) {
            return (ValueTransformer<T>) defaultTransformer;
        }

        // Для перечислений используем общий трансформатор
        if (targetType.isEnum()) {
            return (ValueTransformer<T>) transformers.get(Enum.class);
        }

        return (ValueTransformer<T>) transformers.getOrDefault(targetType, defaultTransformer);
    }

    /**
     * Преобразует строковое значение в указанный тип
     */
    @SuppressWarnings("unchecked")
    public <T> T transform(String value, Class<T> targetType, String params) {
        // Упрощаем с использованием pattern matching (Java 17+)
        if (targetType.isPrimitive()) {
            targetType = (Class<T>) mapPrimitiveToWrapper(targetType);
        }

        // Для перечислений дополняем параметры информацией о классе
        if (targetType.isEnum()) {
            String enumParams = "class=" + targetType.getName();
            if (params != null && !params.isEmpty()) {
                enumParams += "|" + params;
            }
            return (T) getTransformer(Enum.class).transform(value, enumParams);
        }

        // Для остальных типов используем соответствующий трансформатор
        ValueTransformer<T> transformer = getTransformer(targetType);
        return transformer.transform(value, params);
    }

    /**
     * Сопоставляет примитивные типы с их обертками
     */
    private Class<?> mapPrimitiveToWrapper(Class<?> primitiveType) {
        if (primitiveType == int.class) return Integer.class;
        if (primitiveType == long.class) return Long.class;
        if (primitiveType == double.class) return Double.class;
        if (primitiveType == boolean.class) return Boolean.class;
        if (primitiveType == byte.class) return Byte.class;
        if (primitiveType == char.class) return Character.class;
        if (primitiveType == float.class) return Float.class;
        if (primitiveType == short.class) return Short.class;
        return primitiveType;
    }

    /**
     * Проверяет возможность преобразования строки в указанный тип
     */
    public <T> boolean canTransform(String value, Class<T> targetType, String params) {
        ValueTransformer<T> transformer = getTransformer(targetType);
        return transformer.canTransform(value, params);
    }

    /**
     * Преобразует значение в строку
     */
    @SuppressWarnings("unchecked")
    public <T> String toString(T value, String params) {
        if (value == null) return "";

        Class<?> valueType = value.getClass();
        ValueTransformer<T> transformer = (ValueTransformer<T>) getTransformer(valueType);
        return transformer.toString(value, params);
    }
}