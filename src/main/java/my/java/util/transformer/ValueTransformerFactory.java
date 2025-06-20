package my.java.util.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
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
     *
     * @param transformerList список всех доступных трансформаторов
     */
    @Autowired
    public ValueTransformerFactory(List<ValueTransformer<?>> transformerList) {
        // Регистрируем все трансформаторы
        for (ValueTransformer<?> transformer : transformerList) {
            log.debug("Registering transformer for type: {}", transformer.getTargetType().getName());
            transformers.put(transformer.getTargetType(), transformer);
        }

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
     *
     * @param targetType целевой тип
     * @return трансформатор для указанного типа или строковый трансформатор, если не найден
     */
    @SuppressWarnings("unchecked")
    public <T> ValueTransformer<T> getTransformer(Class<T> targetType) {
        if (targetType == null) {
            return (ValueTransformer<T>) defaultTransformer;
        }

        // Приведение перечислений к общему типу Enum
        if (targetType.isEnum()) {
            return (ValueTransformer<T>) transformers.get(Enum.class);
        }

        return (ValueTransformer<T>) transformers.getOrDefault(targetType, defaultTransformer);
    }

    /**
     * Преобразует строковое значение в указанный тип
     *
     * @param value строковое значение
     * @param targetType целевой тип
     * @param params дополнительные параметры
     * @return преобразованное значение или null, если преобразование невозможно
     */
    @SuppressWarnings("unchecked")
    public <T> T transform(String value, Class<T> targetType, String params) {
        if (targetType == String.class) {
            return (T) getTransformer(String.class).transform(value, params);
        } else if (targetType == Integer.class || targetType == int.class) {
            return (T) getTransformer(Integer.class).transform(value, params);
        } else if (targetType == Long.class || targetType == long.class) {
            return (T) getTransformer(Long.class).transform(value, params);
        } else if (targetType == Double.class || targetType == double.class) {
            return (T) getTransformer(Double.class).transform(value, params);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return (T) getTransformer(Boolean.class).transform(value, params);
        } else if (targetType == LocalDate.class) {
            return (T) getTransformer(LocalDate.class).transform(value, params);
        } else if (targetType == LocalTime.class) {
            return (T) getTransformer(LocalTime.class).transform(value, params);
        } else if (targetType == LocalDateTime.class) {
            return (T) getTransformer(LocalDateTime.class).transform(value, params);
        } else if (targetType == ZonedDateTime.class) {
            return (T) getTransformer(ZonedDateTime.class).transform(value, params);
        } else if (targetType.isEnum()) {
            String enumParams = "class=" + targetType.getName();
            if (params != null && !params.isEmpty()) {
                enumParams += "|" + params;
            }
            return (T) getTransformer(Enum.class).transform(value, enumParams);
        } else {
            log.warn("No transformer found for type: {}", targetType.getName());
            return null;
        }
    }

    /**
     * Проверяет возможность преобразования строки в указанный тип
     *
     * @param value строковое значение
     * @param targetType целевой тип
     * @param params дополнительные параметры
     * @return true, если преобразование возможно
     */
    public <T> boolean canTransform(String value, Class<T> targetType, String params) {
        ValueTransformer<T> transformer = getTransformer(targetType);
        return transformer.canTransform(value, params);
    }

    /**
     * Преобразует значение в строку
     *
     * @param value значение для преобразования
     * @param params дополнительные параметры
     * @return строковое представление значения
     */
    @SuppressWarnings("unchecked")
    public <T> String toString(T value, String params) {
        if (value == null) {
            return "";
        }

        Class<?> valueType = value.getClass();
        ValueTransformer<T> transformer = (ValueTransformer<T>) getTransformer(valueType);
        return transformer.toString(value, params);
    }
}