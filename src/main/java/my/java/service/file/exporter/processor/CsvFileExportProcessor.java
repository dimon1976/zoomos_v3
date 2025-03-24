package my.java.service.file.exporter.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.exporter.ExportConfig;
import my.java.service.file.exporter.tracker.ExportProgressTracker;
import org.springframework.stereotype.Component;

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

        Class<T> entityClass = (Class<T>) entities.get(0).getClass();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            // Получение списка полей и заголовков
            List<String> fieldNames = getFieldNames(entityClass, config);
            List<String> headerNames = getHeaderNames(fieldNames, config);

            // Запись заголовка, если нужно
            if (config.isIncludeHeader()) {
                writer.write(formatCsvLine(headerNames));
                writer.newLine();
            }

            // Обработка сущностей с обновлением прогресса
            processEntitiesWithProgress(entities, config, progressTracker, operationId, entity -> {
                List<String> values = fieldNames.stream()
                        .map(fieldName -> {
                            Object value = getFieldValue(entity, fieldName);
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