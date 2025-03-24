package my.java.service.file.exporter.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.exporter.ExportConfig;
import my.java.service.file.exporter.tracker.ExportProgressTracker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Абстрактный класс для процессоров экспорта файлов
 * Путь: /java/my/java/service/file/exporter/processor/AbstractFileExportProcessor.java
 */
@Slf4j
public abstract class AbstractFileExportProcessor<T extends ImportableEntity> implements FileExportProcessor<T> {

    /**
     * Получает список полей для экспорта на основе класса сущности и конфигурации
     */
    protected List<String> getFieldNames(Class<T> entityClass, ExportConfig config) {
        List<String> result = new ArrayList<>();

        // Если указаны конкретные поля для включения, используем их
        if (!config.getIncludedFields().isEmpty()) {
            return new ArrayList<>(config.getIncludedFields());
        }

        // Иначе получаем все доступные поля класса сущности
        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();

            // Пропускаем служебные поля и поля, указанные для исключения
            if (isServiceField(fieldName) || config.getExcludedFields().contains(fieldName)) {
                continue;
            }

            result.add(fieldName);
        }

        return result;
    }

    /**
     * Получает заголовки столбцов для экспорта
     */
    protected List<String> getHeaderNames(List<String> fieldNames, ExportConfig config) {
        List<String> result = new ArrayList<>();

        for (String fieldName : fieldNames) {
            // Если есть маппинг для этого поля, используем его
            if (config.getFieldMappings().containsKey(fieldName)) {
                result.add(config.getFieldMappings().get(fieldName));
            } else {
                // Иначе используем имя поля с большой буквы и разделением слов
                result.add(formatFieldName(fieldName));
            }
        }

        return result;
    }

    /**
     * Форматирует имя поля для отображения в заголовке
     */
    private String formatFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(fieldName.charAt(0)));

        for (int i = 1; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(c);
        }

        return result.toString();
    }

    /**
     * Проверяет, является ли поле служебным
     */
    private boolean isServiceField(String fieldName) {
        return fieldName.equals("id") ||
                fieldName.equals("serialVersionUID") ||
                fieldName.contains("$") ||
                fieldName.equals("hibernateLazyInitializer");
    }

    /**
     * Получает значение поля из сущности
     */
    protected Object getFieldValue(T entity, String fieldName) {
        try {
            // Сначала пытаемся найти геттер для поля
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            Method getter = entity.getClass().getMethod(getterName);
            return getter.invoke(entity);
        } catch (Exception e) {
            try {
                // Если геттер не найден, пытаемся обратиться к полю напрямую
                Field field = findField(entity.getClass(), fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    return field.get(entity);
                }
            } catch (Exception ex) {
                log.error("Ошибка при получении значения поля {}: {}", fieldName, ex.getMessage());
            }
            return null;
        }
    }

    /**
     * Ищет поле в классе и его родительских классах
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Форматирует значение поля в строку с учетом типа данных и настроек форматирования
     */
    protected String formatFieldValue(Object value, String fieldName, ExportConfig config) {
        if (value == null) {
            return "";
        }

        // Если есть специальный формат для этого поля, применяем его
        if (config.getFormatSettings().containsKey(fieldName)) {
            String formatPattern = config.getFormatSettings().get(fieldName);
            return applyFormatPattern(value, formatPattern);
        }

        // Стандартное форматирование в зависимости от типа данных
        if (value instanceof Date) {
            return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format((Date) value);
        } else if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } else if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        } else if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        } else if (value instanceof Boolean) {
            return (Boolean) value ? "Да" : "Нет";
        } else if (value instanceof Number) {
            // Для чисел проверяем, является ли число целым
            if (value instanceof Integer || value instanceof Long) {
                return value.toString();
            } else {
                // Для дробных чисел форматируем с двумя десятичными знаками
                return String.format("%.2f", ((Number) value).doubleValue());
            }
        } else {
            return value.toString();
        }
    }

    /**
     * Применяет шаблон форматирования к значению
     */
    private String applyFormatPattern(Object value, String formatPattern) {
        try {
            if (value instanceof Date) {
                return new SimpleDateFormat(formatPattern).format((Date) value);
            } else if (value instanceof LocalDate) {
                return ((LocalDate) value).format(DateTimeFormatter.ofPattern(formatPattern));
            } else if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value).format(DateTimeFormatter.ofPattern(formatPattern));
            } else if (value instanceof ZonedDateTime) {
                return ((ZonedDateTime) value).format(DateTimeFormatter.ofPattern(formatPattern));
            } else if (value instanceof Number) {
                return String.format(formatPattern, value);
            }
        } catch (Exception e) {
            log.error("Ошибка при форматировании значения {} по шаблону {}: {}", value, formatPattern, e.getMessage());
        }

        return value.toString();
    }

    /**
     * Обрабатывает список сущностей с отслеживанием прогресса
     */
    protected void processEntitiesWithProgress(
            List<T> entities,
            ExportConfig config,
            ExportProgressTracker progressTracker,
            Long operationId,
            ProcessEntityCallback<T> callback) {

        int totalSize = entities.size();
        AtomicInteger processed = new AtomicInteger(0);

        // Устанавливаем общее количество записей
        progressTracker.updateTotal(operationId, totalSize);

        try {
            // Обрабатываем записи пакетами для обновления прогресса
            int batchSize = Math.min(100, Math.max(1, totalSize / 100));  // 1% или не менее 1 записи

            for (T entity : entities) {
                callback.process(entity);

                // Обновляем прогресс каждые batchSize записей
                int current = processed.incrementAndGet();
                if (current % batchSize == 0 || current == totalSize) {
                    progressTracker.updateProgress(operationId, current);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке сущностей: {}", e.getMessage(), e);
            progressTracker.error(operationId, "Ошибка при экспорте: " + e.getMessage());
            throw new RuntimeException("Ошибка при экспорте данных", e);
        }
    }

    /**
     * Интерфейс для обработки одной сущности
     */
    @FunctionalInterface
    protected interface ProcessEntityCallback<T> {
        void process(T entity) throws Exception;
    }
}
