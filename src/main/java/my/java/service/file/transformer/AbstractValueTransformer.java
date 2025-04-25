// src/main/java/my/java/service/file/transformer/AbstractValueTransformer.java
package my.java.service.file.transformer;

import lombok.extern.slf4j.Slf4j;

/**
 * Абстрактная реализация трансформатора значений
 * @param <T> тип, в который будет преобразовано значение
 */
@Slf4j
public abstract class AbstractValueTransformer<T> implements ValueTransformer<T> {

    private final Class<T> targetType;

    protected AbstractValueTransformer(Class<T> targetType) {
        this.targetType = targetType;
    }

    @Override
    public Class<T> getTargetType() {
        return targetType;
    }

    @Override
    public boolean canTransform(String value, String params) {
        if (isEmpty(value)) return true;

        try {
            return transform(value, params) != null || canBeNull();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Определяет, может ли результат преобразования быть null
     */
    protected boolean canBeNull() {
        return true;
    }

    /**
     * Проверяет, является ли строковое значение пустым или null
     */
    protected boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Обрабатывает пустое значение
     */
    protected T handleEmpty(String params) {
        return null;
    }

    /**
     * Получает значение по умолчанию из параметров
     */
    protected String getDefaultValue(String params, String defaultValue) {
        if (params == null || params.trim().isEmpty()) {
            return defaultValue;
        }

        for (String part : params.split("\\|")) {
            if (part.startsWith("default=")) {
                return part.substring("default=".length());
            }
        }

        return defaultValue;
    }
}