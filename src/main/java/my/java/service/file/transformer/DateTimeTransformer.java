// src/main/java/my/java/service/file/transformer/DateTimeTransformer.java
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

    protected DateTimeTransformer(Class<T> targetType) {
        super(targetType);
    }

    @Override
    public T transform(String value, String params) {
        if (isEmpty(value)) return handleEmpty(params);

        // Если указан формат в параметрах, используем его
        String formatPattern = extractPatternFromParams(params);
        if (formatPattern != null) {
            try {
                return parseDateTime(value.trim(), formatPattern);
            } catch (DateTimeParseException e) {
                // Игнорируем и пробуем другие форматы
            }
        }

        // Пробуем стандартные форматы
        for (String pattern : getDefaultPatterns()) {
            try {
                return parseDateTime(value.trim(), pattern);
            } catch (DateTimeParseException e) {
                // Продолжаем перебор
            }
        }

        return null;
    }

    /**
     * Преобразует строку в дату/время с указанным форматом
     */
    protected abstract T parseDateTime(String value, String pattern) throws DateTimeParseException;

    /**
     * Возвращает список шаблонов формата по умолчанию
     */
    protected abstract List<String> getDefaultPatterns();

    /**
     * Извлекает шаблон формата из параметров
     */
    protected String extractPatternFromParams(String params) {
        if (params == null || params.trim().isEmpty()) return null;

        for (String part : params.split("\\|")) {
            if (part.startsWith("pattern=")) {
                return part.substring("pattern=".length());
            }
        }

        return null;
    }
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
        return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
    }

    @Override
    protected List<String> getDefaultPatterns() {
        return DEFAULT_DATE_PATTERNS;
    }

    @Override
    public String toString(LocalDate value, String params) {
        if (value == null) return "";

        String pattern = extractPatternFromParams(params);
        return value.format(DateTimeFormatter.ofPattern(
                pattern != null ? pattern : DEFAULT_DATE_PATTERNS.get(0)));
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
        return LocalTime.parse(value, DateTimeFormatter.ofPattern(pattern));
    }

    @Override
    protected List<String> getDefaultPatterns() {
        return DEFAULT_TIME_PATTERNS;
    }

    @Override
    public String toString(LocalTime value, String params) {
        if (value == null) return "";

        String pattern = extractPatternFromParams(params);
        return value.format(DateTimeFormatter.ofPattern(
                pattern != null ? pattern : DEFAULT_TIME_PATTERNS.get(0)));
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
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
    }

    @Override
    protected List<String> getDefaultPatterns() {
        return DEFAULT_DATETIME_PATTERNS;
    }

    @Override
    public String toString(LocalDateTime value, String params) {
        if (value == null) return "";

        String pattern = extractPatternFromParams(params);
        return value.format(DateTimeFormatter.ofPattern(
                pattern != null ? pattern : DEFAULT_DATETIME_PATTERNS.get(0)));
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

        try {
            return ZonedDateTime.parse(value, formatter);
        } catch (DateTimeParseException e) {
            // Конвертируем LocalDateTime в ZonedDateTime
            return LocalDateTime.parse(value, formatter).atZone(ZoneId.systemDefault());
        }
    }

    @Override
    protected List<String> getDefaultPatterns() {
        return DEFAULT_DATETIME_PATTERNS;
    }

    @Override
    public String toString(ZonedDateTime value, String params) {
        if (value == null) return "";

        String pattern = extractPatternFromParams(params);
        return value.format(DateTimeFormatter.ofPattern(
                pattern != null ? pattern : DEFAULT_DATETIME_PATTERNS.get(0)));
    }
}