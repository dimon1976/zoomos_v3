// src/main/java/my/java/service/file/transformer/NumberTransformer.java
package my.java.service.file.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

/**
 * Базовый трансформатор для числовых значений
 */
@Slf4j
public abstract class NumberTransformer<T extends Number> extends AbstractValueTransformer<T> {

    protected NumberTransformer(Class<T> targetType) {
        super(targetType);
    }

    @Override
    public T transform(String value, String params) {
        if (isEmpty(value)) {
            return handleEmpty(params);
        }

        try {
            return parseNumber(value.trim(), params);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Преобразует строку в число с учетом локали и формата
     */
    protected abstract T parseNumber(String value, String params) throws ParseException;

    /**
     * Получает экземпляр NumberFormat на основе параметров
     */
    protected NumberFormat getNumberFormat(String params) {
        Locale locale = Locale.getDefault();

        // Проверяем локаль
        String localeParam = extractParameter(params, "locale", null);
        if (localeParam != null) {
            String[] localeParts = localeParam.split("_");
            if (localeParts.length == 2) {
                locale = new Locale(localeParts[0], localeParts[1]);
            } else if (localeParts.length == 1) {
                locale = new Locale(localeParts[0]);
            }
        }

        // Формат числа
        String pattern = extractParameter(params, "pattern", null);
        return pattern != null ? new DecimalFormat(pattern) : NumberFormat.getNumberInstance(locale);
    }

    /**
     * Извлекает значение параметра из строки параметров
     */
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
}

/**
 * Трансформатор целых чисел (Integer)
 */
@Component
class IntegerTransformer extends NumberTransformer<Integer> {

    public IntegerTransformer() {
        super(Integer.class);
    }

    @Override
    protected Integer parseNumber(String value, String params) throws ParseException {
        return getNumberFormat(params).parse(value).intValue();
    }

    @Override
    public String toString(Integer value, String params) {
        if (value == null) return "";

        try {
            return getNumberFormat(params).format(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}

/**
 * Трансформатор длинных целых чисел (Long)
 */
@Component
class LongTransformer extends NumberTransformer<Long> {

    public LongTransformer() {
        super(Long.class);
    }

    @Override
    protected Long parseNumber(String value, String params) throws ParseException {
        return getNumberFormat(params).parse(value).longValue();
    }

    @Override
    public String toString(Long value, String params) {
        if (value == null) return "";

        try {
            return getNumberFormat(params).format(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}

/**
 * Трансформатор дробных чисел (Double)
 */
@Component
class DoubleTransformer extends NumberTransformer<Double> {

    public DoubleTransformer() {
        super(Double.class);
    }

    @Override
    protected Double parseNumber(String value, String params) throws ParseException {
        // Заменяем запятую на точку для поддержки разных форматов
        if (value.contains(",") && !value.contains(".")) {
            value = value.replace(',', '.');
        }
        return getNumberFormat(params).parse(value).doubleValue();
    }

    @Override
    public String toString(Double value, String params) {
        if (value == null) return "";

        try {
            return getNumberFormat(params).format(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}