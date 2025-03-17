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

    private static final int PROGRESS_UPDATE_FREQUENCY = 1000;
    private static final double PROGRESS_UPDATE_PERCENTAGE = 0.05;

    private final FileOperationRepository fileOperationRepository;
    private final ClientRepository clientRepository;
    private final FileTypeDetector fileTypeDetector;
    private final PathResolver pathResolver;

    private final Map<Long, FileOperationStatus> activeOperations = new ConcurrentHashMap<>();
    private final Set<Long> canceledOperations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Инициализирует операцию обработки файла
     */
    @Transactional
    public Long initializeFileOperation(MultipartFile file, Long clientId, OperationType operationType)
            throws FileOperationException {
        validateFile(file);
        Client client = findClient(clientId);

        String fileHash = calculateFileHash(file);
        checkForDuplicateFile(clientId, fileHash);

        FileOperation operation = createFileOperation(client, file, operationType);
        FileOperation savedOperation = fileOperationRepository.save(operation);

        Long operationId = savedOperation.getId();

        createFileOperationMetadata(operationId, file, fileHash);
        initializeOperationStatus(operationId);

        log.info("File operation initialized: {}, client: {}, file: {}",
                operationId, clientId, file.getOriginalFilename());

        return operationId;
    }

    /**
     * Обрабатывает файл в соответствии с указанным типом операции
     */
    @Transactional
    public FileProcessingResult processFile(Long operationId, Class<?> entityClass, int chunkSize)
            throws FileOperationException {
        FileOperation operation = findOperation(operationId);
        FileOperationMetadata metadata = findOperationMetadata(operationId);

        updateOperationStatus(operation, OperationStatus.PROCESSING);

        Path filePath = resolveAndValidateFilePath(metadata.getSourceFilePath());
        logFieldDescriptions(entityClass);

        return createSuccessResult(operationId);
    }

    /**
     * Получает информацию о статусе операции
     */
    public FileOperationStatus getOperationStatus(Long operationId) {
        if (activeOperations.containsKey(operationId)) {
            return activeOperations.get(operationId);
        }

        return getOperationStatusFromRepository(operationId);
    }

    /**
     * Отменяет операцию обработки файла
     */
    @Transactional
    public boolean cancelOperation(Long operationId) {
        canceledOperations.add(operationId);

        return fileOperationRepository.findById(operationId)
                .map(operation -> {
                    markOperationAsCanceled(operation);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Проверяет, была ли отменена операция
     */
    public boolean isOperationCanceled(Long operationId) {
        return canceledOperations.contains(operationId);
    }

    /**
     * Обновляет прогресс обработки файла
     */
    public void updateProgress(Long operationId, int processedRecords, int totalRecords) {
        updateOperationMetadataProgress(operationId, processedRecords, totalRecords);
        updateActiveOperationsCache(operationId, processedRecords, totalRecords);

        if (shouldUpdateProgressInDatabase(processedRecords, totalRecords)) {
            updateProgressInDb(operationId, processedRecords, totalRecords);
        }
    }

    /**
     * Обновляет прогресс обработки файла в БД
     */
    @Transactional
    public void updateProgressInDb(Long operationId, int processedRecords, int totalRecords) {
        fileOperationRepository.findById(operationId).ifPresent(operation -> {
            updateOperationMetadataProgress(operationId, processedRecords, totalRecords);
        });
    }

    /**
     * Завершает операцию обработки файла
     */
    @Transactional
    public void completeOperation(Long operationId, OperationStatus status, String message, int recordCount) {
        fileOperationRepository.findById(operationId).ifPresent(operation -> {
            updateOperationWithResult(operation, status, message, recordCount);
            fileOperationRepository.save(operation);

            removeFromTrackingCollections(operationId);
        });
    }

    // Вспомогательные методы

    private void validateFile(MultipartFile file) throws FileOperationException {
        if (file == null || file.isEmpty()) {
            throw new FileOperationException("Файл пуст или не выбран");
        }
    }

    private Client findClient(Long clientId) throws FileOperationException {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new FileOperationException(
                        "Клиент с ID " + clientId + " не найден", "/clients"));
    }

    private void checkForDuplicateFile(Long clientId, String fileHash) {
        boolean duplicateExists = fileOperationRepository.findByClientId(clientId).stream()
                .anyMatch(op -> {
                    FileOperationMetadata metadata = FileOperationMetadata.get(op.getId());
                    return metadata != null && fileHash.equals(metadata.getFileHash()) &&
                            op.getStatus() == OperationStatus.COMPLETED;
                });

        if (duplicateExists) {
            log.warn("Duplicate file detected with hash: {}", fileHash);
        }
    }

    private FileOperation createFileOperation(Client client, MultipartFile file, OperationType operationType) {
        FileTypeDetector.FileType fileType = fileTypeDetector.detectFileType(file);
        log.debug("Detected file type: {}", fileType);

        return FileOperation.builder()
                .client(client)
                .operationType(operationType)
                .fileName(file.getOriginalFilename())
                .fileType(fileType.toString())
                .status(OperationStatus.PENDING)
                .recordCount(0)
                .build();
    }

    private void createFileOperationMetadata(Long operationId, MultipartFile file, String fileHash)
            throws FileOperationException {
        try {
            Path tempFilePath = pathResolver.saveToTempFile(file, "op_" + operationId);

            FileOperationMetadata metadata = FileOperationMetadata.create(operationId);
            metadata.setFileHash(fileHash);
            metadata.setFileSize(file.getSize());
            metadata.setSourceFilePath(tempFilePath.toString());
        } catch (IOException e) {
            throw new FileOperationException("Ошибка при сохранении файла: " + e.getMessage(), e);
        }
    }

    private void initializeOperationStatus(Long operationId) {
        activeOperations.put(operationId, new FileOperationStatus(operationId, 0, 0, OperationStatus.PENDING));
    }

    private FileOperation findOperation(Long operationId) throws FileOperationException {
        return fileOperationRepository.findById(operationId)
                .orElseThrow(() -> new FileOperationException("Операция с ID " + operationId + " не найдена"));
    }

    private FileOperationMetadata findOperationMetadata(Long operationId) throws FileOperationException {
        FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
        if (metadata == null) {
            throw new FileOperationException("Метаданные операции с ID " + operationId + " не найдены");
        }
        return metadata;
    }

    private void updateOperationStatus(FileOperation operation, OperationStatus status) {
        operation.setStatus(status);
        fileOperationRepository.save(operation);

        activeOperations.put(operation.getId(),
                new FileOperationStatus(operation.getId(), 0, 0, status));
    }

    private Path resolveAndValidateFilePath(String sourceFilePath) throws FileOperationException {
        Path filePath = pathResolver.resolveRelativePath(sourceFilePath);
        if (filePath == null || !Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + sourceFilePath);
        }
        return filePath;
    }

    private void logFieldDescriptions(Class<?> entityClass) {
        Map<String, String> fieldDescriptions = FieldDescriptionUtils.getFieldDescriptions(entityClass);
        log.debug("Field descriptions for {}: {}", entityClass.getSimpleName(), fieldDescriptions);
    }

    private FileProcessingResult createSuccessResult(Long operationId) {
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);
        result.setStatus(OperationStatus.COMPLETED);
        result.setMessage("Операция успешно завершена");
        return result;
    }

    private FileOperationStatus getOperationStatusFromRepository(Long operationId) {
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

    private void markOperationAsCanceled(FileOperation operation) {
        operation.setStatus(OperationStatus.FAILED);
        operation.setCompletedAt(ZonedDateTime.now());
        operation.setErrorMessage("Операция отменена пользователем");
        fileOperationRepository.save(operation);

        activeOperations.remove(operation.getId());
    }

    private void updateOperationMetadataProgress(Long operationId, int processedRecords, int totalRecords) {
        FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
        if (metadata != null) {
            metadata.updateProgress(processedRecords, totalRecords);
        }
    }

    private void updateActiveOperationsCache(Long operationId, int processedRecords, int totalRecords) {
        activeOperations.put(operationId, new FileOperationStatus(
                operationId, processedRecords, totalRecords, OperationStatus.PROCESSING));
    }

    private boolean shouldUpdateProgressInDatabase(int processedRecords, int totalRecords) {
        return processedRecords % PROGRESS_UPDATE_FREQUENCY == 0 ||
                (totalRecords > 0 && (double) processedRecords / totalRecords >= PROGRESS_UPDATE_PERCENTAGE);
    }

    private void updateOperationWithResult(FileOperation operation, OperationStatus status,
                                           String message, int recordCount) {
        if (status == OperationStatus.COMPLETED) {
            operation.markAsCompleted(recordCount);
        } else if (status == OperationStatus.FAILED) {
            operation.markAsFailed(message);
        } else {
            operation.setStatus(status);
            operation.setCompletedAt(ZonedDateTime.now());
        }
    }

    private void removeFromTrackingCollections(Long operationId) {
        activeOperations.remove(operationId);
        canceledOperations.remove(operationId);
    }

    private String calculateFileHash(MultipartFile file) throws FileOperationException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return bytesToHexString(hash);
        } catch (Exception e) {
            log.error("Error calculating file hash: {}", e.getMessage());
            throw new FileOperationException("Ошибка при вычислении хеша файла: " + e.getMessage(), e);
        }
    }

    private String bytesToHexString(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Статус операции обработки файла
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
            this.progressPercent = calculateProgressPercent(processedRecords, totalRecords);
        }

        private int calculateProgressPercent(int processed, int total) {
            return total > 0
                    ? (int) Math.min(100, Math.round((double) processed / total * 100))
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
     * Результат обработки файла
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