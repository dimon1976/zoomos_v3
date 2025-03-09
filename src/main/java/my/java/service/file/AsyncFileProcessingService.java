package my.java.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.FileOperation;
import my.java.model.FileOperation.OperationStatus;
import my.java.repository.FileOperationRepository;
import my.java.service.file.FileProcessingService.FileOperationStatus;
import my.java.service.file.FileProcessingService.FileProcessingResult;
import my.java.service.file.strategy.FileProcessingStrategy;
import my.java.service.file.strategy.FileProcessingStrategy.ChunkProcessingResult;
import my.java.service.file.strategy.FileProcessingStrategy.ValidationResult;
import my.java.service.file.strategy.FileProcessingStrategyFactory;
import my.java.util.PathResolver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Асинхронный сервис для обработки файлов.
 * Обеспечивает выполнение длительных операций обработки файлов в отдельных потоках,
 * используя соответствующие стратегии обработки.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncFileProcessingService {

    private final FileProcessingService fileProcessingService;
    private final FileOperationRepository fileOperationRepository;
    private final FileTypeDetector fileTypeDetector;
    private final PathResolver pathResolver;
    private final FileReaderFactory fileReaderFactory;
    private final FileProcessingStrategyFactory strategyFactory;

    // Константа для размера чанка по умолчанию
    private static final int DEFAULT_CHUNK_SIZE = 1000;

    /**
     * Асинхронно обрабатывает файл.
     *
     * @param operationId идентификатор операции
     * @param entityClass класс целевой сущности
     * @return CompletableFuture с результатом обработки
     */
    @Async("fileProcessingExecutor")
    public CompletableFuture<FileProcessingResult> processFileAsync(Long operationId, Class<?> entityClass) {
        log.debug("Начата асинхронная обработка для операции ID: {}, сущность: {}",
                operationId, entityClass.getSimpleName());

        try {
            FileOperation operation = findOperationOrThrow(operationId);
            FileOperationMetadata metadata = findMetadataOrThrow(operationId);
            Path filePath = resolveFilePath(metadata);

            updateOperationStatus(operation, OperationStatus.PROCESSING);

            // Создаем параметры для стратегии
            Map<String, Object> params = createStrategyParams(operationId, entityClass, metadata);

            // Выбираем стратегию обработки
            FileProcessingStrategy strategy = strategyFactory.createStrategy(operation.getOperationType(), params);

            // Обрабатываем файл используя выбранную стратегию
            FileProcessingResult result = processFileWithStrategy(operationId, filePath, strategy, params);

            // Обновляем статус операции в соответствии с результатом
            updateOperationStatusByResult(operationId, result);

            logProcessingCompletion(operationId, result);

            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return handleProcessingError(operationId, e);
        }
    }

    /**
     * Находит операцию по идентификатору или выбрасывает исключение.
     *
     * @param operationId идентификатор операции
     * @return объект операции
     * @throws FileOperationException если операция не найдена
     */
    private FileOperation findOperationOrThrow(Long operationId) throws FileOperationException {
        return fileOperationRepository.findById(operationId)
                .orElseThrow(() -> new FileOperationException("Операция с ID " + operationId + " не найдена"));
    }

    /**
     * Находит метаданные операции или выбрасывает исключение.
     *
     * @param operationId идентификатор операции
     * @return объект метаданных операции
     * @throws FileOperationException если метаданные не найдены
     */
    private FileOperationMetadata findMetadataOrThrow(Long operationId) throws FileOperationException {
        FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
        if (metadata == null) {
            throw new FileOperationException("Метаданные операции с ID " + operationId + " не найдены");
        }
        return metadata;
    }

    /**
     * Разрешает путь к файлу из метаданных.
     *
     * @param metadata метаданные операции
     * @return путь к файлу
     * @throws FileOperationException если файл не найден
     */
    private Path resolveFilePath(FileOperationMetadata metadata) throws FileOperationException {
        Path filePath = pathResolver.resolveRelativePath(metadata.getSourceFilePath());
        if (filePath == null || !Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + metadata.getSourceFilePath());
        }
        return filePath;
    }

    /**
     * Обновляет статус операции.
     *
     * @param operation операция
     * @param status новый статус
     */
    private void updateOperationStatus(FileOperation operation, OperationStatus status) {
        operation.setStatus(status);
        fileOperationRepository.save(operation);
    }

    /**
     * Создает параметры для стратегии обработки.
     *
     * @param operationId идентификатор операции
     * @param entityClass класс целевой сущности
     * @param metadata метаданные операции
     * @return параметры для стратегии
     */
    private Map<String, Object> createStrategyParams(Long operationId, Class<?> entityClass,
                                                     FileOperationMetadata metadata) {
        Map<String, Object> params = new HashMap<>();

        // Добавляем идентификатор операции
        params.put("operationId", operationId);

        // Добавляем тип сущности
        params.put("entityType", getEntityTypeFromClass(entityClass));

        // Добавляем идентификатор клиента из операции
        params.put("clientId", getClientIdFromOperation(operationId));

        // Добавляем идентификатор файла
        params.put("fileId", operationId);

        // Добавляем дополнительные параметры из метаданных
        if (metadata.getAdditionalParams() != null) {
            params.putAll(metadata.getAdditionalParams());
        }

        return params;
    }

    /**
     * Получает тип сущности из класса.
     *
     * @param entityClass класс сущности
     * @return тип сущности
     */
    private String getEntityTypeFromClass(Class<?> entityClass) {
        String simpleName = entityClass.getSimpleName();

        if (simpleName.equals("Product")) {
            return "product";
        } else if (simpleName.equals("CompetitorData")) {
            return "competitor";
        } else if (simpleName.equals("RegionData")) {
            return "region";
        }

        return simpleName.toLowerCase();
    }

    /**
     * Получает идентификатор клиента из операции.
     *
     * @param operationId идентификатор операции
     * @return идентификатор клиента
     */
    private Long getClientIdFromOperation(Long operationId) {
        return fileOperationRepository.findById(operationId)
                .map(operation -> operation.getClient().getId())
                .orElse(null);
    }

    /**
     * Обрабатывает файл используя указанную стратегию.
     *
     * @param operationId идентификатор операции
     * @param filePath путь к файлу
     * @param strategy стратегия обработки
     * @param params параметры стратегии
     * @return результат обработки
     * @throws IOException если произошла ошибка при чтении файла
     */
    private FileProcessingResult processFileWithStrategy(Long operationId, Path filePath,
                                                         FileProcessingStrategy strategy,
                                                         Map<String, Object> params) throws IOException {
        try (FileReader reader = fileReaderFactory.createReader(filePath)) {
            log.debug("Используется ридер: {} для стратегии: {}",
                    reader.getClass().getSimpleName(), strategy.getName());

            List<String> headers = reader.getHeaders();

            // Проверяем заголовки
            ValidationResult validationResult = strategy.validateHeaders(headers, params);
            if (!validationResult.isValid()) {
                return createInvalidHeadersResult(operationId, validationResult);
            }

            long totalRecords = reader.estimateRowCount();
            updateProgress(operationId, 0, (int) totalRecords);

            // Обрабатываем файл чанками
            processFileChunks(operationId, reader, totalRecords, strategy, params);

            // Если операция была отменена, обрабатываем это
            if (fileProcessingService.isOperationCanceled(operationId)) {
                strategy.rollback(operationId, params);
                return createCanceledResult(operationId);
            }

            // Завершаем обработку и возвращаем результат
            return strategy.finishProcessing(operationId, params);
        } catch (Exception e) {
            strategy.rollback(operationId, params);
            throw e;
        }
    }

    /**
     * Создает результат для неверных заголовков.
     *
     * @param operationId идентификатор операции
     * @param validationResult результат валидации
     * @return результат обработки
     */
    private FileProcessingResult createInvalidHeadersResult(Long operationId, ValidationResult validationResult) {
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);
        result.setStatus(OperationStatus.FAILED);
        result.setMessage("Ошибка валидации заголовков файла");

        for (String error : validationResult.getErrors()) {
            result.addError(error);
        }

        return result;
    }

    /**
     * Обрабатывает файл чанками.
     *
     * @param operationId идентификатор операции
     * @param reader ридер файла
     * @param totalRecords общее количество записей
     * @param strategy стратегия обработки
     * @param params параметры стратегии
     * @throws IOException если произошла ошибка при чтении
     */
    private void processFileChunks(Long operationId, FileReader reader, long totalRecords,
                                   FileProcessingStrategy strategy,
                                   Map<String, Object> params) throws IOException {
        int processedRecords = 0;

        // Обрабатываем файл чанками
        while (reader.hasMoreRows()) {
            // Проверяем, не была ли отменена операция
            if (fileProcessingService.isOperationCanceled(operationId)) {
                log.info("Операция {} была отменена", operationId);
                break;
            }

            // Читаем чанк данных
            List<Map<String, String>> chunk = reader.readChunk(DEFAULT_CHUNK_SIZE);
            if (chunk.isEmpty()) {
                break;
            }

            // Обрабатываем данные чанка с помощью стратегии
            ChunkProcessingResult chunkResult = strategy.processChunk(chunk, operationId, params);

            // Обновляем счетчики и прогресс
            processedRecords += chunkResult.getProcessedRecords();
            updateProgress(operationId, processedRecords, (int) totalRecords);

            log.debug("Обработан чанк для операции {}: {} записей", operationId, chunk.size());

            // Если стратегия сигнализирует о необходимости прекратить обработку
            if (!chunkResult.isContinueProcessing()) {
                log.info("Стратегия сигнализирует о прекращении обработки для операции {}", operationId);
                break;
            }
        }
    }

    /**
     * Создает результат отмененной операции.
     *
     * @param operationId идентификатор операции
     * @return результат обработки
     */
    private FileProcessingResult createCanceledResult(Long operationId) {
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);
        result.setStatus(OperationStatus.FAILED);
        result.setMessage("Операция была отменена пользователем");
        return result;
    }

    /**
     * Обновляет прогресс обработки файла.
     *
     * @param operationId идентификатор операции
     * @param processedRecords количество обработанных записей
     * @param totalRecords общее количество записей
     */
    private void updateProgress(Long operationId, int processedRecords, int totalRecords) {
        fileProcessingService.updateProgress(operationId, processedRecords, (int) totalRecords);
    }

    /**
     * Обновляет статус операции на основе результата обработки.
     *
     * @param operationId идентификатор операции
     * @param result результат обработки
     */
    private void updateOperationStatusByResult(Long operationId, FileProcessingResult result) {
        fileProcessingService.completeOperation(operationId, result.getStatus(),
                result.getMessage(), result.getProcessedRecords());
    }

    /**
     * Логирует завершение обработки файла.
     *
     * @param operationId идентификатор операции
     * @param result результат обработки
     */
    private void logProcessingCompletion(Long operationId, FileProcessingResult result) {
        log.info("Асинхронная обработка завершена для операции ID: {}, статус: {}, записей: {}",
                operationId, result.getStatus(), result.getProcessedRecords());
    }

    /**
     * Обрабатывает ошибку при асинхронной обработке файла.
     *
     * @param operationId идентификатор операции
     * @param e исключение
     * @return завершившийся с ошибкой CompletableFuture
     */
    private CompletableFuture<FileProcessingResult> handleProcessingError(Long operationId, Exception e) {
        log.error("Ошибка при асинхронной обработке для операции ID: {}", operationId, e);

        // Обновляем статус операции в случае ошибки
        fileProcessingService.completeOperation(operationId, OperationStatus.FAILED,
                "Ошибка при обработке файла: " + e.getMessage(), 0);

        return CompletableFuture.failedFuture(e);
    }

    /**
     * Запускает асинхронную обработку файла.
     *
     * @param operationId идентификатор операции
     * @param entityClass класс целевой сущности
     * @return результат инициализации обработки
     */
    @Transactional
    public FileOperationStatus startProcessing(Long operationId, Class<?> entityClass) {
        log.debug("Запуск обработки для операции ID: {}, сущность: {}",
                operationId, entityClass.getSimpleName());

        // Запускаем асинхронную обработку
        processFileAsync(operationId, entityClass)
                .thenAccept(result -> {
                    log.info("Обработка завершена для операции ID: {}, статус: {}",
                            operationId, result.getStatus());
                })
                .exceptionally(ex -> {
                    log.error("Обработка не удалась для операции ID: {}", operationId, ex);
                    return null;
                });

        // Возвращаем начальный статус
        return fileProcessingService.getOperationStatus(operationId);
    }
}