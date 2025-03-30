package my.java.service.file.exporter.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.exporter.ExportConfig;
import my.java.service.file.exporter.tracker.ExportProgressTracker;
import org.springframework.stereotype.Component;
import my.java.service.file.exporter.processor.composite.ProductWithRelatedEntitiesExporter.CompositeProductEntity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Процессор для экспорта данных в CSV
 * Путь: /java/my/java/service/file/exporter/processor/CsvFileExportProcessor.java
 */
@Component
@Slf4j
public class CsvFileExportProcessor<T extends ImportableEntity> extends AbstractFileExportProcessor<T> {

    // Символ-разделитель для CSV
    private final String delimiter;
    // Символ-ограничитель для значений
    private final String quoteChar;
    // Символ экранирования для специальных символов
    private final String escapeChar;

    /**
     * Конструктор с настройками по умолчанию
     */
    public CsvFileExportProcessor() {
        this(";", "\"", "\"");
    }

    /**
     * Конструктор с пользовательскими настройками
     */
    public CsvFileExportProcessor(String delimiter, String quoteChar, String escapeChar) {
        this.delimiter = delimiter;
        this.quoteChar = quoteChar;
        this.escapeChar = escapeChar;
    }

    /**
     * Обрабатывает список составных сущностей для экспорта в CSV-файл
     *
     * @param entities список составных сущностей
     * @param config конфигурация экспорта
     * @param outputStream поток для записи результатов
     * @param progressTracker трекер прогресса
     * @param operationId идентификатор операции
     */
    @Override
    public void process(
            List<T> entities,
            ExportConfig config,
            OutputStream outputStream,
            ExportProgressTracker progressTracker,
            Long operationId) {

        if (entities == null || entities.isEmpty()) {
            log.warn("Пустой список сущностей для экспорта в CSV");
            return;
        }

        // Проверяем, имеем ли мы дело с составными сущностями
        boolean isComposite = entities.get(0) instanceof CompositeProductEntity;

        if (isComposite) {
            // Безопасное приведение типов
            @SuppressWarnings("unchecked")
            List<CompositeProductEntity> compositeEntities = (List<CompositeProductEntity>) (List<?>) entities;
            processCompositeEntities(compositeEntities, config, outputStream, progressTracker, operationId);
        } else {
            // Вызываем стандартную обработку для обычных сущностей
            processStandardEntities(entities, config, outputStream, progressTracker, operationId);
        }
    }

    private void processStandardEntities(
            List<T> entities,
            ExportConfig config,
            OutputStream outputStream,
            ExportProgressTracker progressTracker,
            Long operationId) {

        // Здесь должна быть реализация из текущего метода process
        // Для краткости опускаем конкретную реализацию
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            // Получение списка полей и заголовков
            Class<T> entityClass = (Class<T>) entities.get(0).getClass();
            this.entityClass = entityClass; // Сохраняем класс сущности для использования в getHeaderNames

            List<String> fieldNames = getFieldNames(entityClass, config);
            List<String> headerNames = getHeaderNames(fieldNames, config);

            log.debug("Экспорт CSV: выбрано {} полей: {}", fieldNames.size(), fieldNames);
            log.debug("Экспорт CSV: заголовки: {}", headerNames);

            // Запись заголовка, если нужно
            if (config.isIncludeHeader()) {
                writer.write(formatCsvLine(headerNames));
                writer.newLine();
            }

            // Обработка сущностей с обновлением прогресса
            processEntitiesWithProgress(entities, config, progressTracker, operationId, entity -> {
                List<String> values = fieldNames.stream()
                        .map(fieldName -> {
                            // Используем безопасный метод доступа к полям
                            Object value = getFieldValueFromObject(entity, fieldName);
                            return formatFieldValue(value, fieldName, config);
                        })
                        .collect(Collectors.toList());

                writer.write(formatCsvLine(values));
                writer.newLine();
            });

            writer.flush();

            // Обновляем прогресс
            progressTracker.complete(operationId, "Экспорт в CSV завершен успешно");

        } catch (IOException e) {
            log.error("Ошибка при записи данных в CSV: {}", e.getMessage(), e);
            progressTracker.error(operationId, "Ошибка при записи данных: " + e.getMessage());
            throw new RuntimeException("Ошибка при записи данных в CSV", e);
        }
    }

    /**
     * Обрабатывает список составных сущностей
     */
    private void processCompositeEntities(
            List<CompositeProductEntity> entities,
            ExportConfig config,
            OutputStream outputStream,
            ExportProgressTracker progressTracker,
            Long operationId) {

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            // Используем первую сущность как образец для получения полей и заголовков
            CompositeProductEntity sampleEntity = entities.get(0);

            // Получаем список полей и заголовков для составных сущностей
            List<String> fieldNames = getCompositeFieldNames(sampleEntity, config);
            List<String> headerNames = getCompositeHeaderNames(fieldNames, sampleEntity, config);

            log.debug("Экспорт CSV составных сущностей: выбрано {} полей: {}", fieldNames.size(), fieldNames);
            log.debug("Экспорт CSV составных сущностей: заголовки: {}", headerNames);

            // Запись заголовка, если нужно
            if (config.isIncludeHeader()) {
                writer.write(formatCsvLine(headerNames));
                writer.newLine();
            }

            // Создаем счетчик для отслеживания прогресса
            int totalSize = entities.size();
            int processed = 0;
            int batchSize = Math.min(100, Math.max(1, totalSize / 100));  // 1% или не менее 1 записи

            // Устанавливаем общее количество записей
            progressTracker.updateTotal(operationId, totalSize);

            // Обрабатываем каждую сущность
            for (CompositeProductEntity entity : entities) {
                List<String> values = fieldNames.stream()
                        .map(fieldName -> {
                            Object value = getCompositeFieldValue(entity, fieldName);
                            return formatFieldValue(value, fieldName, config);
                        })
                        .collect(Collectors.toList());

                writer.write(formatCsvLine(values));
                writer.newLine();

                // Обновляем прогресс
                processed++;
                if (processed % batchSize == 0 || processed == totalSize) {
                    progressTracker.updateProgress(operationId, processed);
                }
            }

            writer.flush();

            // Обновляем прогресс
            progressTracker.complete(operationId, "Экспорт составных сущностей в CSV завершен успешно");

        } catch (IOException e) {
            log.error("Ошибка при записи составных сущностей в CSV: {}", e.getMessage(), e);
            progressTracker.error(operationId, "Ошибка при записи данных: " + e.getMessage());
            throw new RuntimeException("Ошибка при записи составных сущностей в CSV", e);
        }
    }


    /**
     * Форматирует список значений в строку CSV
     */
    private String formatCsvLine(List<String> values) {
        return values.stream()
                .map(this::escapeCsvValue)
                .collect(Collectors.joining(delimiter));
    }

    /**
     * Экранирует специальные символы в значении для CSV
     */
    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }

        // Проверяем, нужно ли заключать значение в кавычки
        boolean needQuote = value.contains(delimiter) ||
                value.contains(quoteChar) ||
                value.contains("\n") ||
                value.contains("\r");

        if (needQuote) {
            // Заменяем кавычки на двойные кавычки (CSV-стандарт)
            String escapedValue = value.replace(quoteChar, quoteChar + quoteChar);
            return quoteChar + escapedValue + quoteChar;
        } else {
            return value;
        }
    }

    @Override
    public String getFileExtension() {
        return "csv";
    }
}