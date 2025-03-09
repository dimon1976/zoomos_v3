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
        Locale locale = Locale.getDefault();

        // Проверяем, указана ли локаль в параметрах
        if (params != null && params.contains("locale=")) {
            String localeParam = extractParameter(params, "locale", null);
            if (localeParam != null) {
                // Формат локали: "язык_страна", например "ru_RU"
                String[] localeParts = localeParam.split("_");
                if (localeParts.length == 2) {
                    locale = new Locale(localeParts[0], localeParts[1]);
                } else if (localeParts.length == 1) {
                    locale = new Locale(localeParts[0]);
                }
            }
        }

        // Получаем формат числа
        String pattern = extractParameter(params, "pattern", null);
        if (pattern != null) {
            // Используем указанный шаблон
            return new DecimalFormat(pattern);
        } else {
            // Используем стандартный числовой формат для локали
            return NumberFormat.getNumberInstance(locale);
        }
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

        // Параметры в формате "param1=value1|param2=value2|..."
        String[] parts = params.split("\\|");
        for (String part : parts) {
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
        if (value == null) {
            return "";
        }

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
        if (value == null) {
            return "";
        }

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
            return value.toString();
        }
    }
}