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
 * Используется по умолчанию, если не указана конкретная стратегия.
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
        // Поддерживаем все типы файлов, которые поддерживаются зарегистрированными процессорами
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

        log.info("Обработка файла стандартной стратегией: {}, тип сущности: {}", filePath, entityType);

        // Проверяем существование файла
        if (!Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + filePath);
        }

        // Получаем подходящий процессор для файла
        FileProcessor processor = processorFactory.createProcessor(filePath)
                .orElseThrow(() -> new FileOperationException("Не найден подходящий процессор для файла: " + filePath));

        // Создаем FileReadingOptions из параметров
        FileReadingOptions options = params != null ? FileReadingOptions.fromMap(params) : new FileReadingOptions();

        // Устанавливаем стратегию обработки
        configureProcessingParams(options);

        try {
            // Обрабатываем файл и получаем сущности
            List<ImportableEntity> entities = processor.processFileWithOptions(
                    filePath, entityType, client, fieldMapping, options, operation);

            log.info("Файл успешно обработан, создано {} сущностей", entities.size());
            return entities;
        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при обработке файла: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> analyzeFile(Path filePath) {
        log.debug("Анализ файла: {}", filePath);

        // Проверяем существование файла
        if (!Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + filePath);
        }

        // Получаем подходящий процессор для файла
        FileProcessor processor = processorFactory.createProcessor(filePath)
                .orElseThrow(() -> new FileOperationException("Не найден подходящий процессор для файла: " + filePath));

        // Создаем пустой FileReadingOptions
        FileReadingOptions options = new FileReadingOptions();

        try {
            // Анализируем файл с использованием FileReadingOptions
            Map<String, Object> analysis = processor.analyzeFileWithOptions(filePath, options);

            // Добавляем информацию о стратегии
            analysis.put("strategy", getStrategyId());
            analysis.put("strategyName", getDisplayName());

            return analysis;
        } catch (Exception e) {
            log.error("Ошибка при анализе файла: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при анализе файла: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isCompatibleWithFile(Path filePath) {
        // Проверяем, есть ли подходящий процессор для файла
        return processorFactory.createProcessor(filePath).isPresent();
    }

    @Override
    public Map<String, Object> getConfigurableParameters() {
        Map<String, Object> params = new HashMap<>();

        // Параметры обработки ошибок
        Map<String, String> errorHandlingOptions = new HashMap<>();
        errorHandlingOptions.put("stop", "Остановить при первой ошибке");
        errorHandlingOptions.put("continue", "Пропускать ошибочные записи и продолжать");
        errorHandlingOptions.put("report", "Собирать ошибки в отчет и продолжать");

        params.put("errorHandling", errorHandlingOptions);

        // Параметры обработки дубликатов
        Map<String, String> duplicateHandlingOptions = new HashMap<>();
        duplicateHandlingOptions.put("skip", "Пропускать дубликаты");
        duplicateHandlingOptions.put("update", "Обновлять существующие записи");
        duplicateHandlingOptions.put("error", "Выдавать ошибку при дубликате");

        params.put("duplicateHandling", duplicateHandlingOptions);

        // Параметры стратегии обработки
        Map<String, String> processingStrategyOptions = new HashMap<>();
        processingStrategyOptions.put("insert", "Только вставка новых записей");
        processingStrategyOptions.put("update", "Обновление существующих записей");
        processingStrategyOptions.put("upsert", "Вставка новых и обновление существующих");
        processingStrategyOptions.put("replace", "Замена всех существующих записей");

        params.put("processingStrategy", processingStrategyOptions);

        // Параметры размера пакета
        Map<String, String> batchSizeOptions = new HashMap<>();
        batchSizeOptions.put("100", "100 записей");
        batchSizeOptions.put("500", "500 записей");
        batchSizeOptions.put("1000", "1000 записей");
        batchSizeOptions.put("5000", "5000 записей");

        params.put("batchSize", batchSizeOptions);

        // Параметры валидации
        params.put("validateData", "Валидировать данные перед импортом");
        params.put("trimWhitespace", "Удалять лишние пробелы в строковых полях");

        return params;
    }

    @Override
    public boolean validateParameters(Map<String, String> params) {
        // Проверяем обязательные параметры
        if (params == null) {
            return false;
        }

        // Проверяем допустимость значений параметров
        if (params.containsKey("errorHandling")) {
            String errorHandling = params.get("errorHandling");
            if (!Arrays.asList("stop", "continue", "report").contains(errorHandling)) {
                log.warn("Недопустимое значение параметра errorHandling: {}", errorHandling);
                return false;
            }
        }

        if (params.containsKey("duplicateHandling")) {
            String duplicateHandling = params.get("duplicateHandling");
            if (!Arrays.asList("skip", "update", "error").contains(duplicateHandling)) {
                log.warn("Недопустимое значение параметра duplicateHandling: {}", duplicateHandling);
                return false;
            }
        }

        if (params.containsKey("processingStrategy")) {
            String processingStrategy = params.get("processingStrategy");
            if (!Arrays.asList("insert", "update", "upsert", "replace").contains(processingStrategy)) {
                log.warn("Недопустимое значение параметра processingStrategy: {}", processingStrategy);
                return false;
            }
        }

        if (params.containsKey("batchSize")) {
            try {
                int batchSize = Integer.parseInt(params.get("batchSize"));
                if (batchSize <= 0) {
                    log.warn("Недопустимое значение параметра batchSize: {}", batchSize);
                    return false;
                }
            } catch (NumberFormatException e) {
                log.warn("Параметр batchSize не является целым числом: {}", params.get("batchSize"));
                return false;
            }
        }

        return true;
    }

    @Override
    public long estimateMemoryRequirements(Path filePath) {
        try {
            // Получаем размер файла
            long fileSize = Files.size(filePath);

            // Оцениваем требуемую память (примерно 2x от размера файла для обработки в памяти)
            return fileSize * 2;
        } catch (Exception e) {
            log.error("Ошибка при оценке требуемой памяти: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public int getPriority() {
        // Стандартная стратегия имеет самый низкий приоритет
        return 0;
    }

    /**
     * Настраивает параметры обработки на основе переданных параметров или значений по умолчанию.
     *
     * @param options параметры обработки
     */
    private void configureProcessingParams(FileReadingOptions options) {
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