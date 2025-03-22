package my.java.service.file.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Трансформатор для значений даты и времени
 * @param <T> конкретный тип даты/времени (LocalDate, LocalDateTime, и т.д.)
 */
@Slf4j
public abstract class DateTimeTransformer<T> extends AbstractValueTransformer<T> {

    protected static final List<String> DEFAULT_DATE_PATTERNS = Arrays.asList(
            "dd.MM.yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy/MM/dd"
    );

    protected static final List<String> DEFAULT_TIME_PATTERNS = Arrays.asList(
            "HH:mm:ss", "HH:mm", "hh:mm:ss a", "hh:mm a"
    );

    protected static final List<String> DEFAULT_DATETIME_PATTERNS = Arrays.asList(
            "dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss",
            "dd.MM.yyyy HH:mm", "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm:ss", "MM/dd/yyyy HH:mm:ss"
    );

    /**
     * Конструктор трансформатора дат
     *
     * @param targetType тип данных даты/времени
     */
    protected DateTimeTransformer(Class<T> targetType) {
        super(targetType);
    }

    @Override
    public T transform(String value, String params) {
        if (isEmpty(value)) {
            return handleEmpty(params);
        }

        value = value.trim();

        // Если указан формат в параметрах, используем его
        String formatPattern = extractParameter(params, "pattern", null);
        if (formatPattern != null) {
            T result = tryParseWithPattern(value, formatPattern);
            if (result != null) {
                return result;
            }
        }

        // Иначе пробуем различные стандартные форматы
        return tryParseWithDefaultPatterns(value);
    }

    /**
     * Пытается преобразовать строку с заданным шаблоном
     *
     * @param value строковое значение
     * @param pattern шаблон формата
     * @return преобразованное значение или null в случае неудачи
     */
    private T tryParseWithPattern(String value, String pattern) {
        try {
            return parseDateTime(value, pattern);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse date/time '{}' with pattern '{}': {}",
                    value, pattern, e.getMessage());
            return null;
        }
    }

    /**
     * Пытается преобразовать строку используя шаблоны по умолчанию
     *
     * @param value строковое значение
     * @return преобразованное значение или null в случае неудачи
     */
    private T tryParseWithDefaultPatterns(String value) {
        for (String pattern : getDefaultPatterns()) {
            try {
                return parseDateTime(value, pattern);
            } catch (DateTimeParseException e) {
                // Игнорируем и продолжаем пробовать другие форматы
            }
        }

        log.warn("Could not parse date/time from '{}' using any of the standard patterns", value);
        return null;
    }

    /**
     * Преобразует строку в дату/время с использованием указанного формата
     *
     * @param value строковое представление даты/времени
     * @param pattern шаблон формата
     * @return преобразованное значение даты/времени
     * @throws DateTimeParseException если преобразование невозможно
     */
    protected abstract T parseDateTime(String value, String pattern) throws DateTimeParseException;

    /**
     * Возвращает список шаблонов формата по умолчанию для этого типа даты/времени
     *
     * @return список шаблонов формата
     */
    protected abstract List<String> getDefaultPatterns();

    @Override
    public String toString(T value, String params) {
        if (value == null) {
            return "";
        }

        String pattern = extractParameter(params, "pattern", null);
        if (pattern == null) {
            pattern = getDefaultPatterns().get(0); // Используем первый формат по умолчанию
        }

        return formatDateTime(value, pattern);
    }

    /**
     * Форматирует дату/время в строку с использованием указанного шаблона
     *
     * @param value значение даты/времени
     * @param pattern шаблон формата
     * @return отформатированная строка
     */
    protected abstract String formatDateTime(T value, String pattern);
}

/**
 * Трансформатор для LocalDate
 */
@Component
class LocalDateTransformer extends DateTimeTransformer<LocalDate> {

    public LocalDateTransformer() {
        super(LocalDate.class);
    }

    @Override
    protected LocalDate parseDateTime(String value, String pattern) throws DateTimeParseException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDate.parse(value, formatter);
    }

    @Override
    protected List<String> getDefaultPatterns() {
        return DEFAULT_DATE_PATTERNS;
    }

    @Override
    protected String formatDateTime(LocalDate value, String pattern) {
        return value.format(DateTimeFormatter.ofPattern(pattern));
    }
}

/**
 * Трансформатор для LocalTime
 */
@Component
class LocalTimeTransformer extends DateTimeTransformer<LocalTime> {

    public LocalTimeTransformer() {
        super(LocalTime.class);
    }

    @Override
    protected LocalTime parseDateTime(String value, String pattern) throws DateTimeParseException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalTime.parse(value, formatter);
    }

    @Override
    protected List<String> getDefaultPatterns() {
        return DEFAULT_TIME_PATTERNS;
    }

    @Override
    protected String formatDateTime(LocalTime value, String pattern) {
        return value.format(DateTimeFormatter.ofPattern(pattern));
    }
}

/**
 * Трансформатор для LocalDateTime
 */
@Component
class LocalDateTimeTransformer extends DateTimeTransformer<LocalDateTime> {

    public LocalDateTimeTransformer() {
        super(LocalDateTime.class);
    }

    @Override
    protected LocalDateTime parseDateTime(String value, String pattern) throws DateTimeParseException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.parse(value, formatter);
    }

    @Override
    protected List<String> getDefaultPatterns() {
        return DEFAULT_DATETIME_PATTERNS;
    }

    @Override
    protected String formatDateTime(LocalDateTime value, String pattern) {
        return value.format(DateTimeFormatter.ofPattern(pattern));
    }
}

/**
 * Трансформатор для ZonedDateTime
 */
@Component
class ZonedDateTimeTransformer extends DateTimeTransformer<ZonedDateTime> {

    public ZonedDateTimeTransformer() {
        super(ZonedDateTime.class);
    }

    @Override
    protected ZonedDateTime parseDateTime(String value, String pattern) throws DateTimeParseException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        // Сначала пробуем преобразовать в ZonedDateTime
        try {
            return ZonedDateTime.parse(value, formatter);
        } catch (DateTimeParseException e) {
            // Если не получилось, преобразуем в LocalDateTime и добавляем текущую зону
            LocalDateTime localDateTime = LocalDateTime.parse(value, formatter);
            return localDateTime.atZone(ZoneId.systemDefault());
        }
    }

    @Override
    protected List<String> getDefaultPatterns() {
        return DEFAULT_DATETIME_PATTERNS;
    }

    @Override
    protected String formatDateTime(ZonedDateTime value, String pattern) {
        return value.format(DateTimeFormatter.ofPattern(pattern));
    }
}