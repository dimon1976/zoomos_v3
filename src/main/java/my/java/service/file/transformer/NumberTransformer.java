package my.java.service.file.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

/**
 * Трансформатор для числовых значений
 * @param <T> конкретный числовой тип (Integer, Double, и т.д.)
 */
@Slf4j
public abstract class NumberTransformer<T extends Number> extends AbstractValueTransformer<T> {

    /**
     * Конструктор трансформатора чисел
     *
     * @param targetType тип числовых данных
     */
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
            log.warn("Could not parse number from '{}': {}", value, e.getMessage());
            return null;
        }
    }

    /**
     * Преобразует строку в число с учетом локали и формата
     *
     * @param value строковое представление числа
     * @param params параметры форматирования
     * @return преобразованное число
     * @throws ParseException если преобразование невозможно
     */
    protected abstract T parseNumber(String value, String params) throws ParseException;

    /**
     * Получает экземпляр NumberFormat на основе указанных параметров
     *
     * @param params параметры форматирования
     * @return настроенный NumberFormat
     */
    protected NumberFormat getNumberFormat(String params) {
        Locale locale = getLocaleFromParams(params);
        String pattern = extractParameter(params, "pattern", null);

        if (pattern != null) {
            DecimalFormat format = (DecimalFormat) NumberFormat.getNumberInstance(locale);
            format.applyPattern(pattern);
            return format;
        } else {
            return NumberFormat.getNumberInstance(locale);
        }
    }

    /**
     * Извлекает локаль из параметров
     *
     * @param params параметры форматирования
     * @return локаль из параметров или локаль по умолчанию
     */
    protected Locale getLocaleFromParams(String params) {
        String localeParam = extractParameter(params, "locale", null);
        if (localeParam == null) {
            return Locale.getDefault();
        }

        String[] localeParts = localeParam.split("_");
        if (localeParts.length == 2) {
            return new Locale(localeParts[0], localeParts[1]);
        } else if (localeParts.length == 1) {
            return new Locale(localeParts[0]);
        }

        return Locale.getDefault();
    }
}

/**
 * Трансформатор целых чисел (Integer)
 */
@Component
@Slf4j
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
        if (value == null) {
            return "";
        }

        try {
            return getNumberFormat(params).format(value);
        } catch (Exception e) {
            log.warn("Error formatting Integer value: {}", e.getMessage());
            return value.toString();
        }
    }
}

/**
 * Трансформатор длинных целых чисел (Long)
 */
@Component
@Slf4j
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
        if (value == null) {
            return "";
        }

        try {
            return getNumberFormat(params).format(value);
        } catch (Exception e) {
            log.warn("Error formatting Long value: {}", e.getMessage());
            return value.toString();
        }
    }
}

/**
 * Трансформатор дробных чисел (Double)
 */
@Component
@Slf4j
class DoubleTransformer extends NumberTransformer<Double> {

    public DoubleTransformer() {
        super(Double.class);
    }

    @Override
    protected Double parseNumber(String value, String params) throws ParseException {
        return getNumberFormat(params).parse(value).doubleValue();
    }

    @Override
    public String toString(Double value, String params) {
        if (value == null) {
            return "";
        }

        try {
            return getNumberFormat(params).format(value);
        } catch (Exception e) {
            log.warn("Error formatting Double value: {}", e.getMessage());
            return value.toString();
        }
    }
}