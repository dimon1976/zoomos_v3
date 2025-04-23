// src/main/java/my/java/service/file/strategy/DefaultFileProcessingStrategy.java
package my.java.service.file.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.options.FileReadingOptions;
import my.java.service.file.processor.FileProcessor;
import my.java.service.file.processor.FileProcessorFactory;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Стандартная стратегия обработки файлов.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultFileProcessingStrategy implements FileProcessingStrategy {

    private final FileProcessorFactory processorFactory;

    @Override
    public String getStrategyId() {
        return "default";
    }

    @Override
    public String getDisplayName() {
        return "Стандартная стратегия";
    }

    @Override
    public String getDescription() {
        return "Базовая стратегия для обработки файлов с настраиваемыми параметрами";
    }

    @Override
    public List<String> getSupportedFileTypes() {
        return new ArrayList<>(processorFactory.getSupportedFileExtensions());
    }

    @Override
    public List<ImportableEntity> processFile(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            Map<String, String> params,
            FileOperation operation) {

        // Создаем FileReadingOptions из параметров
        FileReadingOptions options = params != null ?
                FileReadingOptions.fromMap(params) : new FileReadingOptions();

        return processFileWithOptions(filePath, entityType, client, fieldMapping, options, operation);
    }

    /**
     * Обрабатывает файл с использованием FileReadingOptions
     */
    public List<ImportableEntity> processFileWithOptions(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            FileReadingOptions options,
            FileOperation operation) {

        // Проверяем существование файла
        if (!Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + filePath);
        }

        // Получаем подходящий процессор для файла
        FileProcessor processor = processorFactory.createProcessor(filePath)
                .orElseThrow(() -> new FileOperationException("Не найден подходящий процессор для файла"));

        // Настраиваем параметры обработки
        configureProcessingOptions(options);

        try {
            // Обрабатываем файл и получаем сущности
            return processor.processFileWithOptions(
                    filePath, entityType, client, fieldMapping, options, operation);
        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage());
            throw new FileOperationException("Ошибка при обработке файла: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> analyzeFile(Path filePath) {
        // Проверяем существование файла
        if (!Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + filePath);
        }

        // Получаем подходящий процессор для файла
        FileProcessor processor = processorFactory.createProcessor(filePath)
                .orElseThrow(() -> new FileOperationException("Не найден подходящий процессор для файла"));

        try {
            // Анализируем файл с минимальными параметрами
            Map<String, Object> analysis = processor.analyzeFileWithOptions(filePath, new FileReadingOptions());
            analysis.put("strategy", getStrategyId());
            analysis.put("strategyName", getDisplayName());
            return analysis;
        } catch (Exception e) {
            log.error("Ошибка при анализе файла: {}", e.getMessage());
            throw new FileOperationException("Ошибка при анализе файла: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isCompatibleWithFile(Path filePath) {
        return processorFactory.createProcessor(filePath).isPresent();
    }

    @Override
    public Map<String, Object> getConfigurableParameters() {
        Map<String, Object> params = new HashMap<>();

        // Параметры обработки ошибок
        params.put("errorHandling", Map.of(
                "stop", "Остановить при первой ошибке",
                "continue", "Пропускать ошибочные записи и продолжать",
                "report", "Собирать ошибки в отчет и продолжать"
        ));

        // Параметры обработки дубликатов
        params.put("duplicateHandling", Map.of(
                "skip", "Пропускать дубликаты",
                "update", "Обновлять существующие записи",
                "error", "Выдавать ошибку при дубликате"
        ));

        // Параметры стратегии обработки
        params.put("processingStrategy", Map.of(
                "insert", "Только вставка новых записей",
                "update", "Обновление существующих записей",
                "upsert", "Вставка новых и обновление существующих",
                "replace", "Замена всех существующих записей"
        ));

        // Параметры размера пакета
        params.put("batchSize", Map.of(
                "100", "100 записей",
                "500", "500 записей",
                "1000", "1000 записей",
                "5000", "5000 записей"
        ));

        // Другие параметры
        params.put("validateData", "Валидировать данные перед импортом");
        params.put("trimWhitespace", "Удалять лишние пробелы в строковых полях");

        return params;
    }

    @Override
    public boolean validateParameters(Map<String, String> params) {
        if (params == null) return false;

        // Преобразуем в FileReadingOptions и используем его валидацию
        FileReadingOptions options = FileReadingOptions.fromMap(params);
        return options.isValid();
    }

    @Override
    public long estimateMemoryRequirements(Path filePath) {
        try {
            // Получаем размер файла
            long fileSize = Files.size(filePath);
            // Оцениваем требуемую память (примерно 2x от размера файла)
            return fileSize * 2;
        } catch (Exception e) {
            log.warn("Ошибка при оценке требуемой памяти: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public int getPriority() {
        return 0; // Самый низкий приоритет
    }

    /**
     * Настраивает параметры обработки на основе переданных параметров или значений по умолчанию.
     */
    private void configureProcessingOptions(FileReadingOptions options) {
        // Устанавливаем значения по умолчанию, если не указаны
        if (!options.hasAdditionalParam("errorHandling")) {
            options.getAdditionalParams().put("errorHandling", "continue");
        }

        if (options.getDuplicateHandling() == null || options.getDuplicateHandling().isEmpty()) {
            options.setDuplicateHandling("skip");
        }

        if (options.getProcessingStrategy() == null || options.getProcessingStrategy().isEmpty()) {
            options.setProcessingStrategy("insert");
        }

        if (options.getBatchSize() <= 0) {
            options.setBatchSize(500);
        }

        if (!options.hasAdditionalParam("validateData")) {
            options.getAdditionalParams().put("validateData", "true");
        }

        if (!options.hasAdditionalParam("trimWhitespace")) {
            options.getAdditionalParams().put("trimWhitespace", "true");
        }
    }
}