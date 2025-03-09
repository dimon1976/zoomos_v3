package my.java.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.FileOperation.OperationStatus;
import my.java.model.FileOperation.OperationType;
import my.java.repository.ClientRepository;
import my.java.repository.FileOperationRepository;
import my.java.util.FieldDescriptionUtils;
import my.java.util.PathResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для обработки файлов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileProcessingService {

    private final FileOperationRepository fileOperationRepository;
    private final ClientRepository clientRepository;
    private final FileTypeDetector fileTypeDetector;
    private final PathResolver pathResolver;

    // Хранилище активных операций для отслеживания
    private final Map<Long, FileOperationStatus> activeOperations = new ConcurrentHashMap<>();

    // Хранилище отмененных операций
    private final Set<Long> canceledOperations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Инициализирует операцию обработки файла и возвращает идентификатор операции
     *
     * @param file файл для обработки
     * @param clientId идентификатор клиента
     * @param operationType тип операции
     * @return идентификатор созданной операции
     * @throws FileOperationException если произошла ошибка при инициализации
     */
    @Transactional
    public Long initializeFileOperation(MultipartFile file, Long clientId, OperationType operationType) throws FileOperationException {
        try {
            // Проверяем файл
            if (file == null || file.isEmpty()) {
                throw new FileOperationException("Файл пуст или не выбран");
            }

            // Получаем клиента
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new FileOperationException("Клиент с ID " + clientId + " не найден", "/clients"));

            // Определяем тип файла
            FileTypeDetector.FileType fileType = fileTypeDetector.detectFileType(file);
            log.debug("Detected file type: {}", fileType);

            // Вычисляем хеш содержимого файла
            String fileHash = calculateFileHash(file);

            // Проверяем наличие файла с таким же хешем для этого клиента
            // Используем COMPLETED напрямую, так как это enum в существующей модели
            boolean duplicateExists = fileOperationRepository.findByClientId(clientId).stream()
                    .anyMatch(op -> {
                        // Получаем метаданные операции, если они существуют
                        FileOperationMetadata metadata = FileOperationMetadata.get(op.getId());
                        return metadata != null && fileHash.equals(metadata.getFileHash()) &&
                                op.getStatus() == OperationStatus.COMPLETED;
                    });

            if (duplicateExists) {
                log.warn("Duplicate file detected with hash: {}", fileHash);
                // В данном случае мы просто логируем дубликат, но можно также выбросить исключение
                // throw new FileOperationException("Файл с таким содержимым уже был обработан");
            }

            // Создаем запись об операции
            FileOperation operation = FileOperation.builder()
                    .client(client)
                    .operationType(operationType)
                    .fileName(file.getOriginalFilename())
                    .fileType(fileType.toString())
                    .status(OperationStatus.PENDING)
                    .recordCount(0) // Устанавливаем пустое значение для количества записей
                    .build();

            // Сохраняем операцию
            FileOperation savedOperation = fileOperationRepository.save(operation);
            Long operationId = savedOperation.getId();

            // Сохраняем файл во временную директорию
            Path tempFilePath = pathResolver.saveToTempFile(file, "op_" + operationId);

            // Создаем метаданные операции
            FileOperationMetadata metadata = FileOperationMetadata.create(operationId);
            metadata.setFileHash(fileHash);
            metadata.setFileSize(file.getSize());
            metadata.setSourceFilePath(tempFilePath.toString());

            // Инициализируем статус операции
            activeOperations.put(operationId, new FileOperationStatus(operationId, 0, 0, OperationStatus.PENDING));

            log.info("File operation initialized: {}, client: {}, file: {}",
                    operationId, clientId, file.getOriginalFilename());

            return operationId;
        } catch (Exception e) {
            log.error("Error initializing file operation: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при инициализации обработки файла: " + e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает файл в соответствии с указанным типом операции
     *
     * @param operationId идентификатор операции
     * @param entityClass класс целевой сущности
     * @param chunkSize размер чанка для обработки
     * @return результат обработки файла
     * @throws FileOperationException если произошла ошибка при обработке
     */
    @Transactional
    public FileProcessingResult processFile(Long operationId, Class<?> entityClass, int chunkSize) throws FileOperationException {
        // Получаем операцию из БД
        FileOperation operation = fileOperationRepository.findById(operationId)
                .orElseThrow(() -> new FileOperationException("Операция с ID " + operationId + " не найдена"));

        // Получаем метаданные операции
        FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
        if (metadata == null) {
            throw new FileOperationException("Метаданные операции с ID " + operationId + " не найдены");
        }

        // Обновляем статус операции на "в процессе"
        operation.setStatus(OperationStatus.PROCESSING);
        fileOperationRepository.save(operation);

        // Обновляем статус в кеше
        activeOperations.put(operationId, new FileOperationStatus(operationId, 0, 0, OperationStatus.PROCESSING));

        // Получаем путь к файлу
        Path filePath = pathResolver.resolveRelativePath(metadata.getSourceFilePath());
        if (filePath == null || !Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + metadata.getSourceFilePath());
        }

        // Для демонстрации - показываем сопоставление полей
        Map<String, String> fieldDescriptions = FieldDescriptionUtils.getFieldDescriptions(entityClass);
        log.debug("Field descriptions for {}: {}", entityClass.getSimpleName(), fieldDescriptions);

        // Здесь будет реализация обработки файла в соответствии с типом операции
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);
        result.setStatus(OperationStatus.COMPLETED);
        result.setMessage("Операция успешно завершена");

        // Возвращаем результат
        return result;
    }

    /**
     * Получает информацию о статусе операции
     *
     * @param operationId идентификатор операции
     * @return статус операции
     */
    public FileOperationStatus getOperationStatus(Long operationId) {
        // Если операция активна, возвращаем из кеша
        if (activeOperations.containsKey(operationId)) {
            return activeOperations.get(operationId);
        }

        // Иначе получаем из БД и метаданных
        return fileOperationRepository.findById(operationId)
                .map(operation -> {
                    FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
                    int processedRecords = metadata != null && metadata.getProcessedRecords() != null
                            ? metadata.getProcessedRecords() : 0;
                    int totalRecords = metadata != null && metadata.getTotalRecords() != null
                            ? metadata.getTotalRecords() : 0;
                    return new FileOperationStatus(operationId, processedRecords, totalRecords, operation.getStatus());
                })
                .orElse(new FileOperationStatus(operationId, 0, 0, OperationStatus.FAILED));
    }

    /**
     * Отменяет операцию обработки файла
     *
     * @param operationId идентификатор операции
     * @return true, если операция успешно отменена
     */
    @Transactional
    public boolean cancelOperation(Long operationId) {
        // Отмечаем операцию как отмененную
        canceledOperations.add(operationId);

        // Обновляем статус в БД
        return fileOperationRepository.findById(operationId)
                .map(operation -> {
                    // Задаем статус как FAILED, так как CANCELED не существует в текущем enum
                    operation.setStatus(OperationStatus.FAILED);
                    operation.setCompletedAt(ZonedDateTime.now());
                    operation.setErrorMessage("Операция отменена пользователем");
                    fileOperationRepository.save(operation);

                    // Удаляем из активных операций
                    activeOperations.remove(operationId);

                    return true;
                })
                .orElse(false);
    }

    /**
     * Проверяет, была ли отменена операция
     *
     * @param operationId идентификатор операции
     * @return true, если операция была отменена
     */
    public boolean isOperationCanceled(Long operationId) {
        return canceledOperations.contains(operationId);
    }

    /**
     * Обновляет прогресс обработки файла
     *
     * @param operationId идентификатор операции
     * @param processedRecords количество обработанных записей
     * @param totalRecords общее количество записей
     */
    public void updateProgress(Long operationId, int processedRecords, int totalRecords) {
        // Получаем метаданные операции
        FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
        if (metadata != null) {
            metadata.updateProgress(processedRecords, totalRecords);
        }

        // Обновляем статус в кеше
        activeOperations.put(operationId, new FileOperationStatus(
                operationId, processedRecords, totalRecords, OperationStatus.PROCESSING));

        // Периодически обновляем статус в БД (например, каждые 5% или 1000 записей)
        if (processedRecords % 1000 == 0 ||
                (totalRecords > 0 && (double) processedRecords / totalRecords >= 0.05)) {
            updateProgressInDb(operationId, processedRecords, totalRecords);
        }
    }

    /**
     * Обновляет прогресс обработки файла в БД
     *
     * @param operationId идентификатор операции
     * @param processedRecords количество обработанных записей
     * @param totalRecords общее количество записей
     */
    @Transactional
    public void updateProgressInDb(Long operationId, int processedRecords, int totalRecords) {
        fileOperationRepository.findById(operationId).ifPresent(operation -> {
            // Сохраняем прогресс в метаданных
            FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
            if (metadata != null) {
                metadata.updateProgress(processedRecords, totalRecords);
            }
        });
    }

    /**
     * Завершает операцию обработки файла
     *
     * @param operationId идентификатор операции
     * @param status статус завершения
     * @param message сообщение о результате
     * @param recordCount количество обработанных записей
     */
    @Transactional
    public void completeOperation(Long operationId, OperationStatus status, String message, int recordCount) {
        fileOperationRepository.findById(operationId).ifPresent(operation -> {
            if (status == OperationStatus.COMPLETED) {
                operation.markAsCompleted(recordCount);
            } else if (status == OperationStatus.FAILED) {
                operation.markAsFailed(message);
            } else {
                operation.setStatus(status);
                operation.setCompletedAt(ZonedDateTime.now());
            }

            fileOperationRepository.save(operation);

            // Удаляем из активных операций
            activeOperations.remove(operationId);
            canceledOperations.remove(operationId);
        });
    }

    /**
     * Вычисляет хеш содержимого файла
     *
     * @param file файл для вычисления хеша
     * @return строка с хешем файла
     * @throws IOException если произошла ошибка при чтении файла
     */
    private String calculateFileHash(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());

            // Преобразуем хеш в строку
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            log.error("Error calculating file hash: {}", e.getMessage());
            throw new IOException("Ошибка при вычислении хеша файла", e);
        }
    }

    /**
     * Класс для хранения статуса операции обработки файла
     */
    public static class FileOperationStatus {
        private final Long operationId;
        private final int processedRecords;
        private final int totalRecords;
        private final OperationStatus status;
        private final int progressPercent;

        public FileOperationStatus(Long operationId, int processedRecords, int totalRecords, OperationStatus status) {
            this.operationId = operationId;
            this.processedRecords = processedRecords;
            this.totalRecords = totalRecords;
            this.status = status;

            // Вычисляем процент выполнения
            this.progressPercent = totalRecords > 0
                    ? (int) Math.min(100, Math.round((double) processedRecords / totalRecords * 100))
                    : 0;
        }

        public Long getOperationId() {
            return operationId;
        }

        public int getProcessedRecords() {
            return processedRecords;
        }

        public int getTotalRecords() {
            return totalRecords;
        }

        public OperationStatus getStatus() {
            return status;
        }

        public int getProgressPercent() {
            return progressPercent;
        }
    }

    /**
     * Класс для хранения результата обработки файла
     */
    public static class FileProcessingResult {
        private Long operationId;
        private OperationStatus status;
        private String message;
        private int processedRecords;
        private int successRecords;
        private int failedRecords;
        private List<String> errors;

        public FileProcessingResult() {
            this.errors = new ArrayList<>();
        }

        public Long getOperationId() {
            return operationId;
        }

        public void setOperationId(Long operationId) {
            this.operationId = operationId;
        }

        public OperationStatus getStatus() {
            return status;
        }

        public void setStatus(OperationStatus status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getProcessedRecords() {
            return processedRecords;
        }

        public void setProcessedRecords(int processedRecords) {
            this.processedRecords = processedRecords;
        }

        public int getSuccessRecords() {
            return successRecords;
        }

        public void setSuccessRecords(int successRecords) {
            this.successRecords = successRecords;
        }

        public int getFailedRecords() {
            return failedRecords;
        }

        public void setFailedRecords(int failedRecords) {
            this.failedRecords = failedRecords;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
    }
}