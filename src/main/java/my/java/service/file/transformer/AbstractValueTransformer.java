package my.java.service.file.transformer;

import lombok.extern.slf4j.Slf4j;

/**
 * Абстрактная реализация трансформатора значений
 * @param <T> тип, в который будет преобразовано значение
 */
@Slf4j
public abstract class AbstractValueTransformer<T> implements ValueTransformer<T> {

    private final Class<T> targetType;

    /**
     * Конструктор абстрактного трансформатора
     *
     * @param targetType целевой тип данных
     */
    protected AbstractValueTransformer(Class<T> targetType) {
        this.targetType = targetType;
    }

    @Override
    public Class<T> getTargetType() {
        return targetType;
    }

    @Override
    public boolean canTransform(String value, String params) {
        if (isEmpty(value)) {
            return true; // Пустые значения всегда можно преобразовать (в null)
        }

        try {
            T result = transform(value, params);
            return result != null || canBeNull();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Определяет, может ли результат преобразования быть null
     *
     * @return true, если результат может быть null
     */
    protected boolean canBeNull() {
        return true;
    }

    /**
     * Проверяет, является ли строковое значение пустым или null
     *
     * @param value проверяемое значение
     * @return true, если значение пустое или null
     */
    protected boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Обрабатывает пустое значение - возвращает null или значение по умолчанию
     *
     * @param params параметры для определения значения по умолчанию
     * @return значение по умолчанию или null
     */
    protected T handleEmpty(String params) {
        return null;
    }

    /**
     * Извлекает значение параметра из строки параметров
     *
     * @param params строка параметров
     * @param paramName имя параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра или значение по умолчанию
     */
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

    /**
     * Пытается получить значение по умолчанию из параметров
     *
     * @param params строка параметров
     * @param defaultValue значение по умолчанию, если параметр не указан
     * @return значение по умолчанию
     */
    protected String getDefaultValue(String params, String defaultValue) {
        return extractParameter(params, "default", defaultValue);
    }
}