package my.java.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.FileOperation;
import my.java.model.FileOperation.OperationStatus;
import my.java.repository.FileOperationRepository;
import my.java.service.file.FileProcessingService.FileOperationStatus;
import my.java.service.file.FileProcessingService.FileProcessingResult;
import my.java.util.FieldDescriptionUtils;
import my.java.util.PathResolver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Асинхронный сервис для обработки файлов
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

    /**
     * Асинхронно обрабатывает файл
     *
     * @param operationId идентификатор операции
     * @param entityClass класс целевой сущности
     * @return CompletableFuture с результатом обработки
     */
    @Async("fileProcessingExecutor")
    public CompletableFuture<FileProcessingResult> processFileAsync(Long operationId, Class<?> entityClass) {
        log.debug("Starting async processing for operation ID: {}, entity: {}",
                operationId, entityClass.getSimpleName());

        try {
            // Получаем операцию
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new FileOperationException("Операция с ID " + operationId + " не найдена"));

            // Получаем метаданные операции
            FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
            if (metadata == null) {
                throw new FileOperationException("Метаданные операции с ID " + operationId + " не найдены");
            }

            // Путь к файлу
            Path filePath = pathResolver.resolveRelativePath(metadata.getSourceFilePath());
            if (filePath == null || !Files.exists(filePath)) {
                throw new FileOperationException("Файл не найден: " + metadata.getSourceFilePath());
            }

            // Обновляем статус операции
            operation.setStatus(OperationStatus.PROCESSING);
            fileOperationRepository.save(operation);

            // Запускаем чтение и обработку файла
            FileProcessingResult result = processFileInChunks(operationId, filePath, entityClass);

            // Обновляем статус операции
            if (result.getStatus() == OperationStatus.COMPLETED) {
                fileProcessingService.completeOperation(operationId, OperationStatus.COMPLETED,
                        result.getMessage(), result.getProcessedRecords());
            } else {
                fileProcessingService.completeOperation(operationId, result.getStatus(),
                        result.getMessage(), result.getProcessedRecords());
            }

            log.info("Async processing completed for operation ID: {}, status: {}, records: {}",
                    operationId, result.getStatus(), result.getProcessedRecords());

            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Error in async processing for operation ID: {}", operationId, e);

            // Обновляем статус операции в случае ошибки
            fileProcessingService.completeOperation(operationId, OperationStatus.FAILED,
                    "Ошибка при обработке файла: " + e.getMessage(), 0);

            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Обрабатывает файл чанками
     *
     * @param operationId идентификатор операции
     * @param filePath путь к файлу
     * @param entityClass класс целевой сущности
     * @return результат обработки
     * @throws IOException если произошла ошибка при чтении файла
     */
    @Transactional
    public FileProcessingResult processFileInChunks(Long operationId, Path filePath,
                                                    Class<?> entityClass) throws IOException {

        // Инициализируем результат
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);

        // Размер чанка
        int chunkSize = 1000;

        // Инициализируем счетчики
        int processedRecords = 0;
        int successRecords = 0;
        int failedRecords = 0;

        // Открываем файл для чтения, используя фабрику
        try (FileReader reader = fileReaderFactory.createReader(filePath)) {
            // Получаем заголовки файла
            List<String> headers = reader.getHeaders();
            log.debug("File headers: {}", headers);

            // Получаем сопоставление полей
            Map<String, String> fieldDescriptions = FieldDescriptionUtils.getDescriptionToFieldMap(entityClass);
            log.debug("Field descriptions mapping: {}", fieldDescriptions);

            // Оцениваем общее количество записей
            long totalRecords = reader.estimateRowCount();
            log.debug("Estimated total records: {}", totalRecords);

            // Обновляем информацию о прогрессе
            fileProcessingService.updateProgress(operationId, 0, (int) totalRecords);

            // Обрабатываем файл чанками
            while (reader.hasMoreRows()) {
                // Проверяем, не была ли отменена операция
                if (fileProcessingService.isOperationCanceled(operationId)) {
                    log.info("Operation {} was canceled", operationId);
                    result.setStatus(OperationStatus.FAILED); // Используем FAILED вместо CANCELED
                    result.setMessage("Операция была отменена пользователем");
                    result.setProcessedRecords(processedRecords);
                    result.setSuccessRecords(successRecords);
                    result.setFailedRecords(failedRecords);
                    return result;
                }

                // Читаем чанк данных
                List<Map<String, String>> chunk = reader.readChunk(chunkSize);
                if (chunk.isEmpty()) {
                    break;
                }

                // Обрабатываем данные чанка (здесь будет бизнес-логика обработки)
                for (Map<String, String> row : chunk) {
                    try {
                        // Здесь будет логика преобразования строки данных в сущность
                        // и сохранения в БД

                        // Заглушка для демонстрации
                        successRecords++;
                    } catch (Exception e) {
                        log.error("Error processing row: {}", e.getMessage());
                        failedRecords++;
                    }

                    processedRecords++;
                }

                // Обновляем прогресс
                fileProcessingService.updateProgress(operationId, processedRecords, (int) totalRecords);

                log.debug("Processed chunk for operation {}: {} records", operationId, chunk.size());
            }

            // Устанавливаем результат
            result.setStatus(OperationStatus.COMPLETED);
            result.setMessage("Обработка файла успешно завершена");
            result.setProcessedRecords(processedRecords);
            result.setSuccessRecords(successRecords);
            result.setFailedRecords(failedRecords);

            return result;
        } catch (Exception e) {
            log.error("Error processing file: {}", e.getMessage(), e);

            result.setStatus(OperationStatus.FAILED);
            result.setMessage("Ошибка при обработке файла: " + e.getMessage());
            result.setProcessedRecords(processedRecords);
            result.setSuccessRecords(successRecords);
            result.setFailedRecords(failedRecords);

            return result;
        }
    }

    /**
     * Запускает асинхронную обработку файла
     *
     * @param operationId идентификатор операции
     * @param entityClass класс целевой сущности
     * @return результат инициализации обработки
     */
    @Transactional
    public FileOperationStatus startProcessing(Long operationId, Class<?> entityClass) {
        log.debug("Starting processing for operation ID: {}, entity: {}",
                operationId, entityClass.getSimpleName());

        // Запускаем асинхронную обработку
        processFileAsync(operationId, entityClass)
                .thenAccept(result -> {
                    log.info("Processing completed for operation ID: {}, status: {}",
                            operationId, result.getStatus());
                })
                .exceptionally(ex -> {
                    log.error("Processing failed for operation ID: {}", operationId, ex);
                    return null;
                });

        // Возвращаем начальный статус
        return fileProcessingService.getOperationStatus(operationId);
    }
}