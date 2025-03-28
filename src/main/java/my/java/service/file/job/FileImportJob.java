package my.java.service.file.job;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;
import my.java.repository.FileOperationRepository;
import my.java.service.file.entity.EntitySaverFactory;
import my.java.service.file.processor.FileProcessor;
import my.java.service.file.processor.FileProcessorFactory;
import my.java.service.file.strategy.FileProcessingStrategy;
import my.java.service.file.tracker.ImportProgressTracker;
import my.java.util.PathResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Задача для асинхронного выполнения импорта файла.
 * Реализует интерфейс Callable для возможности выполнения в ExecutorService.
 */
@Slf4j
public class FileImportJob implements Callable<FileOperationDto> {

    private final FileOperation operation;
    private final Path filePath;
    private final Client client;
    private final Map<String, String> fieldMapping;
    private final Map<String, String> params;
    private final String entityType;

    private final FileOperationRepository fileOperationRepository;
    private final FileProcessorFactory processorFactory;
    private final List<FileProcessingStrategy> processingStrategies;
    private final ImportProgressTracker progressTracker;
    private final PathResolver pathResolver;
    private final EntitySaverFactory entitySaverFactory;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Builder
    public FileImportJob(
            FileOperation operation,
            Path filePath,
            Client client,
            Map<String, String> fieldMapping,
            Map<String, String> params,
            String entityType,
            FileOperationRepository fileOperationRepository,
            FileProcessorFactory processorFactory,
            List<FileProcessingStrategy> processingStrategies,
            ImportProgressTracker progressTracker,
            PathResolver pathResolver,
            EntitySaverFactory entitySaverFactory) {

        this.operation = operation;
        this.filePath = filePath;
        this.client = client;
        this.fieldMapping = fieldMapping;
        this.params = params;
        this.entityType = entityType;
        this.fileOperationRepository = fileOperationRepository;
        this.processorFactory = processorFactory;
        this.processingStrategies = processingStrategies;
        this.progressTracker = progressTracker;
        this.pathResolver = pathResolver;
        this.entitySaverFactory = entitySaverFactory;
    }

    /**
     * Отменяет выполнение задачи.
     */
    public void cancel() {
        this.cancelled.set(true);
    }

    /**
     * Проверяет, была ли задача отменена.
     *
     * @return true, если задача была отменена
     */
    public boolean isCancelled() {
        return this.cancelled.get();
    }

    /**
     * Выполняет задачу импорта файла.
     *
     * @return DTO с информацией о результате операции
     * @throws Exception если произошла ошибка при выполнении
     */
    @Override
    public FileOperationDto call() throws Exception {
        log.info("НАЧАЛО ВЫПОЛНЕНИЯ ЗАДАЧИ ИМПОРТА ДЛЯ ОПЕРАЦИИ #{} - поток {}",
                operation.getId(), Thread.currentThread().getName());

        try {
            // Проверяем существование файла
            if (!Files.exists(filePath)) {
                throw new FileOperationException("Файл не найден: " + filePath);
            }

            // Получаем подходящий процессор для файла
            FileProcessor processor = processorFactory.createProcessor(filePath)
                    .orElseThrow(() -> new FileOperationException("Не найден подходящий процессор для файла: " + filePath));

            // Получаем подходящую стратегию обработки
            FileProcessingStrategy strategy = selectProcessingStrategy(filePath);

            // Проверяем валидность файла
            if (!processor.validateFile(filePath)) {
                throw new FileOperationException("Файл не прошел валидацию: " + filePath);
            }

            // Оцениваем количество записей
            int estimatedRecords = processor.estimateRecordCount(filePath);

            // Обновляем количество записей в операции и трекере прогресса
            operation.setTotalRecords(estimatedRecords);
            fileOperationRepository.save(operation);

            // Инициализируем/обновляем прогресс с оцененным количеством записей
            progressTracker.updateProgress(operation.getId(), 0);
            log.info("Оценено количество записей: {} для операции #{}", estimatedRecords, operation.getId());

            // Добавляем небольшую задержку для обновления UI
            Thread.sleep(200);

            // Обрабатываем файл
            List<ImportableEntity> entities = processor.processFile(
                    filePath, entityType, client, fieldMapping, params, operation);

            log.debug("Файл обработан, получено {} сущностей", entities.size());

            // Проверяем отмену операции
            if (cancelled.get()) {
                throw new FileOperationException("Операция отменена пользователем");
            }

            // Сохраняем сущности в БД используя соответствующий сервис из фабрики
            int savedCount = saveEntities(entities);

            // Обновляем статус операции
            operation.markAsCompleted(savedCount);
            operation.setCompletedAt(ZonedDateTime.now());
            fileOperationRepository.save(operation);

            // Завершаем отслеживание прогресса
            progressTracker.completeProgress(operation.getId(), true, entities.size(), savedCount, null);

            // Перемещаем файл из временной директории (если это временный файл)
            if (filePath.toString().contains("temp-files")) {
                Path permanentPath = pathResolver.moveFromTempToUpload(
                        filePath, "imported_" + client.getId() + "_" + operation.getId());
                operation.setResultFilePath(permanentPath.toString());
                fileOperationRepository.save(operation);
            }

            log.info("Задача импорта успешно выполнена для операции #{}, сохранено {} сущностей",
                    operation.getId(), savedCount);

            // Возвращаем DTO с информацией об операции
            return mapToDto(operation);
        } catch (Exception e) {
            log.error("Ошибка при выполнении задачи импорта для операции #{}: {}",
                    operation.getId(), e.getMessage(), e);

            // Обновляем статус операции
            operation.markAsFailed(e.getMessage());
            operation.setCompletedAt(ZonedDateTime.now());
            fileOperationRepository.save(operation);

            // Завершаем отслеживание прогресса
            progressTracker.completeProgress(operation.getId(), false, 0, 0, e.getMessage());

            throw e;
        }
    }

    /**
     * Выбирает стратегию обработки для файла.
     *
     * @param filePath путь к файлу
     * @return подходящая стратегия обработки
     * @throws FileOperationException если не найдена подходящая стратегия
     */
    private FileProcessingStrategy selectProcessingStrategy(Path filePath) {
        Long strategyId = getStrategyIdFromParams();

        if (strategyId != null) {
            // Ищем стратегию по ID
            for (FileProcessingStrategy strategy : processingStrategies) {
                if (strategy.getStrategyId().equals(strategyId.toString())) {
                    return strategy;
                }
            }
        }

        // Ищем подходящую стратегию по совместимости с файлом
        List<FileProcessingStrategy> compatibleStrategies = new ArrayList<>();
        for (FileProcessingStrategy strategy : processingStrategies) {
            if (strategy.isCompatibleWithFile(filePath)) {
                compatibleStrategies.add(strategy);
            }
        }

        if (compatibleStrategies.isEmpty()) {
            throw new FileOperationException("Не найдена подходящая стратегия обработки для файла: " + filePath);
        }

        // Сортируем стратегии по приоритету (более высокий приоритет в начале)
        compatibleStrategies.sort(Comparator.comparingInt(FileProcessingStrategy::getPriority).reversed());

        return compatibleStrategies.get(0);
    }

    /**
     * Получает ID стратегии из параметров.
     *
     * @return ID стратегии или null, если не указан
     */
    private Long getStrategyIdFromParams() {
        if (params != null && params.containsKey("strategyId")) {
            try {
                return Long.parseLong(params.get("strategyId"));
            } catch (NumberFormatException e) {
                log.warn("Невозможно преобразовать strategyId к Long: {}", params.get("strategyId"));
            }
        }
        return null;
    }

    /**
     * Сохраняет сущности в БД.
     * Упрощенная версия без использования TransactionManager.
     *
     * @param entities список сущностей для сохранения
     * @return количество сохраненных сущностей
     */
    private int saveEntities(List<ImportableEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            log.warn("Получен пустой список сущностей для сохранения");
            return 0;
        }

        log.info("Начинаем сохранение {} сущностей типа {}", entities.size(), entityType);

        // Получаем функцию для сохранения сущностей указанного типа из фабрики
        Function<List<ImportableEntity>, Integer> saveFunction = entitySaverFactory.getSaver(entityType.toLowerCase());

        if (saveFunction == null) {
            log.error("Не найдена функция сохранения для типа: {}", entityType.toLowerCase());
            return 0;
        }

        // Логируем информацию о функции сохранения
        log.debug("Получена функция сохранения: {}", saveFunction.getClass().getName());

        // Определяем размер пакета для сохранения
        int batchSize = getBatchSizeFromParams();

        int totalSaved = 0;
        List<ImportableEntity> batch = new ArrayList<>(batchSize);

        // Инициализируем прогресс-трекер с более точным количеством сущностей
        log.info("Запуск сохранения {} сущностей, размер пакета: {}", entities.size(), batchSize);
        progressTracker.updateProgress(operation.getId(), 0);

        for (int i = 0; i < entities.size(); i++) {
            // Проверяем отмену операции
            if (cancelled.get()) {
                log.info("Операция отменена пользователем, прекращаем сохранение сущностей");
                break;
            }

            // Добавляем сущность в текущий пакет
            batch.add(entities.get(i));

            // Если пакет заполнен или это последняя сущность, сохраняем пакет
            if (batch.size() >= batchSize || i == entities.size() - 1) {
                try {
                    // Более детальное логирование перед сохранением
                    log.debug("Пытаемся сохранить пакет размером {}", batch.size());

                    // Проверяем первую сущность в пакете для отладки
                    if (!batch.isEmpty()) {
                        ImportableEntity firstEntity = batch.get(0);
                        log.debug("Пример сущности для сохранения: тип={}, client={}",
                                firstEntity.getClass().getSimpleName(),
                                client.getId());
                    }

                    // Сохраняем пакет
                    Integer savedInBatch = saveFunction.apply(new ArrayList<>(batch));

                    // Проверяем результат сохранения
                    log.debug("Результат сохранения пакета: {}", savedInBatch);

                    if (savedInBatch != null) {
                        totalSaved += savedInBatch;
                        log.debug("Всего сохранено на данный момент: {}", totalSaved);
                    } else {
                        log.warn("Сохранение пакета вернуло null, возможна проблема с сохранением");
                    }

                    // Обновляем прогресс
                    int processedCount = i + 1;
                    progressTracker.updateProgress(operation.getId(), processedCount);

                    // Логируем прогресс каждые 1000 сущностей или при завершении
                    if (processedCount % 1000 == 0 || i == entities.size() - 1) {
                        int percent = entities.size() > 0 ? (processedCount * 100 / entities.size()) : 0;
                        log.info("Прогресс импорта операции #{}: обработано {} из {} сущностей ({}%), сохранено: {}",
                                operation.getId(), processedCount, entities.size(), percent, totalSaved);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при сохранении пакета сущностей: {}", e.getMessage(), e);
                    // Пробуем сохранить записи поодиночке при ошибке пакетного сохранения
                    if (batch.size() > 1) {
                        log.info("Попытка сохранения записей поодиночке");
                        for (ImportableEntity entity : batch) {
                            try {
                                List<ImportableEntity> singleEntity = Collections.singletonList(entity);
                                log.debug("Сохраняем одиночную сущность: {}", entity.getClass().getSimpleName());
                                Integer saved = saveFunction.apply(singleEntity);
                                log.debug("Результат сохранения одиночной сущности: {}", saved);
                                if (saved != null && saved > 0) {
                                    totalSaved += saved;
                                }
                            } catch (Exception singleError) {
                                log.error("Ошибка при сохранении отдельной записи: {} - {}",
                                        entity.getClass().getSimpleName(),
                                        singleError.getMessage());
                            }
                        }
                    }
                }

                // Очищаем пакет
                batch.clear();
            }
        }

        log.info("Завершено сохранение сущностей. Всего сохранено: {}", totalSaved);
        return totalSaved;
    }

    /**
     * Получает размер пакета из параметров.
     *
     * @return размер пакета
     */
    private int getBatchSizeFromParams() {
        if (params != null && params.containsKey("batchSize")) {
            try {
                int batchSize = Integer.parseInt(params.get("batchSize"));
                if (batchSize > 0) {
                    return batchSize;
                }
            } catch (NumberFormatException e) {
                log.warn("Невозможно преобразовать batchSize к int: {}", params.get("batchSize"));
            }
        }
        return 500; // Значение по умолчанию
    }

    /**
     * Преобразует модель операции в DTO.
     *
     * @param operation операция
     * @return DTO операции
     */
    private FileOperationDto mapToDto(FileOperation operation) {
        if (operation == null) {
            return null;
        }

        return FileOperationDto.builder()
                .id(operation.getId())
                .clientId(operation.getClient() != null ? operation.getClient().getId() : null)
                .clientName(operation.getClient() != null ? operation.getClient().getName() : null)
                .operationType(operation.getOperationType())
                .fileName(operation.getFileName())
                .fileType(operation.getFileType())
                .recordCount(operation.getRecordCount())
                .status(operation.getStatus())
                .errorMessage(operation.getErrorMessage())
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .processingProgress(operation.getProcessingProgress())
                .processedRecords(operation.getProcessedRecords())
                .totalRecords(operation.getTotalRecords())
                .build();
    }

    /**
     * Класс, содержащий информацию о задаче импорта.
     */
    @Getter
    public static class JobInfo {
        private final Long operationId;
        private final ZonedDateTime startTime;
        private final FileImportJob job;

        public JobInfo(Long operationId, ZonedDateTime startTime, FileImportJob job) {
            this.operationId = operationId;
            this.startTime = startTime;
            this.job = job;
        }
    }
}