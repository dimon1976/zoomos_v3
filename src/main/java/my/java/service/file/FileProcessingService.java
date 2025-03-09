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
 * Service for file processing operations.
 * Handles initialization, execution, and management of file operations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileProcessingService {

    private final FileOperationRepository fileOperationRepository;
    private final ClientRepository clientRepository;
    private final FileTypeDetector fileTypeDetector;
    private final PathResolver pathResolver;

    // Track active and canceled operations using thread-safe collections
    private final Map<Long, FileOperationStatus> activeOperations = new ConcurrentHashMap<>();
    private final Set<Long> canceledOperations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Константы для работы с операциями
    private static final int PROGRESS_UPDATE_THRESHOLD = 1000; // Порог обновления прогресса
    private static final double PROGRESS_PERCENTAGE_UPDATE = 0.05; // 5% прогресса

    /**
     * Initializes a file operation.
     *
     * @param file File to process
     * @param clientId Client identifier
     * @param operationType Type of operation
     * @return ID of the created operation
     * @throws FileOperationException If initialization fails
     */
    /**
     * Инициализирует операцию обработки файла.
     *
     * @param file          Файл для обработки
     * @param clientId      Идентификатор клиента
     * @param operationType Тип операции
     * @return Идентификатор созданной операции
     * @throws FileOperationException Если инициализация не удалась
     */
    @Transactional
    public Long initializeFileOperation(MultipartFile file, Long clientId, OperationType operationType)
            throws FileOperationException {
        validateFileInput(file);

        Client client = findClientOrThrow(clientId);
        FileTypeDetector.FileType fileType = determineFileType(file);
        String fileHash = calculateFileHash(file);

        checkForDuplicateFile(clientId, fileHash);

        FileOperation operation = createFileOperation(client, operationType, file, fileType);
        FileOperation savedOperation = fileOperationRepository.save(operation);
        Long operationId = savedOperation.getId();

        // Сохраняем файл во временную директорию
        Path tempFilePath = saveFileToTempDirectory(file, operationId);

        // Инициализируем метаданные операции
        initializeOperationMetadata(operationId, fileHash, file.getSize(), tempFilePath);

        // Инициализируем статус операции
        activeOperations.put(operationId, new FileOperationStatus(operationId, 0, 0, OperationStatus.PENDING));

        log.info("Инициализирована операция с файлом: {}, клиент: {}, файл: {}",
                operationId, clientId, file.getOriginalFilename());

        return operationId;
    }

    /**
     * Проверяет входной файл на валидность.
     *
     * @param file Файл для проверки
     * @throws FileOperationException Если файл не валиден
     */
    private void validateFileInput(MultipartFile file) throws FileOperationException {
        if (file == null || file.isEmpty()) {
            throw new FileOperationException("Файл пуст или не выбран");
        }
    }

    /**
     * Находит клиента по идентификатору или выбрасывает исключение.
     *
     * @param clientId Идентификатор клиента
     * @return Объект клиента
     * @throws FileOperationException Если клиент не найден
     */
    private Client findClientOrThrow(Long clientId) throws FileOperationException {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new FileOperationException(
                        "Клиент с ID " + clientId + " не найден", "/clients"));
    }

    /**
     * Определяет тип файла.
     *
     * @param file Файл для определения типа
     * @return Тип файла
     * @throws FileOperationException Если тип не может быть определен
     */
    private FileTypeDetector.FileType determineFileType(MultipartFile file) throws FileOperationException {
        try {
            FileTypeDetector.FileType fileType = fileTypeDetector.detectFileType(file);
            log.debug("Определен тип файла: {}", fileType);
            return fileType;
        } catch (Exception e) {
            log.error("Ошибка при определении типа файла: {}", e.getMessage());
            throw new FileOperationException("Ошибка при определении типа файла: " + e.getMessage());
        }
    }

    /**
     * Проверяет наличие дубликатов файла для данного клиента.
     *
     * @param clientId Идентификатор клиента
     * @param fileHash Хеш файла
     * @throws FileOperationException Если файл является дубликатом
     */
    private void checkForDuplicateFile(Long clientId, String fileHash) {
        boolean duplicateExists = fileOperationRepository.findByClientId(clientId).stream()
                .anyMatch(op -> {
                    FileOperationMetadata metadata = FileOperationMetadata.get(op.getId());
                    return metadata != null && fileHash.equals(metadata.getFileHash()) &&
                            op.getStatus() == OperationStatus.COMPLETED;
                });

        if (duplicateExists) {
            log.warn("Обнаружен дубликат файла с хешем: {}", fileHash);
            // Здесь мы только логируем дубликат, но можно и выбросить исключение
            // throw new FileOperationException("Файл с таким содержимым уже был обработан");
        }
    }

    /**
     * Создает объект операции с файлом.
     *
     * @param client        Клиент
     * @param operationType Тип операции
     * @param file          Файл
     * @param fileType      Тип файла
     * @return Созданный объект операции
     */
    private FileOperation createFileOperation(Client client, OperationType operationType,
                                              MultipartFile file, FileTypeDetector.FileType fileType) {
        return FileOperation.builder()
                .client(client)
                .operationType(operationType)
                .fileName(file.getOriginalFilename())
                .fileType(fileType.toString())
                .status(OperationStatus.PENDING)
                .recordCount(0)
                .build();
    }

    /**
     * Сохраняет файл во временную директорию.
     *
     * @param file        Файл для сохранения
     * @param operationId Идентификатор операции
     * @return Путь к сохраненному файлу
     * @throws FileOperationException Если сохранение не удалось
     */
    private Path saveFileToTempDirectory(MultipartFile file, Long operationId) throws FileOperationException {
        try {
            Path tempFilePath = pathResolver.saveToTempFile(file, "op_" + operationId);
            log.debug("Файл сохранен во временную директорию: {}", tempFilePath);
            return tempFilePath;
        } catch (IOException e) {
            log.error("Ошибка при сохранении файла: {}", e.getMessage());
            throw new FileOperationException("Ошибка при сохранении файла: " + e.getMessage());
        }
    }

    /**
     * Инициализирует метаданные операции.
     *
     * @param operationId Идентификатор операции
     * @param fileHash    Хеш файла
     * @param fileSize    Размер файла
     * @param filePath    Путь к файлу
     */
    private void initializeOperationMetadata(Long operationId, String fileHash,
                                             long fileSize, Path filePath) {
        FileOperationMetadata metadata = FileOperationMetadata.create(operationId);
        metadata.setFileHash(fileHash);
        metadata.setFileSize(fileSize);
        metadata.setSourceFilePath(filePath.toString());
    }

    /**
     * Обрабатывает файл в соответствии с указанным типом операции.
     *
     * @param operationId Идентификатор операции
     * @param entityClass Класс целевой сущности
     * @param chunkSize   Размер чанка для обработки
     * @return Результат обработки файла
     * @throws FileOperationException Если произошла ошибка при обработке
     */
    @Transactional
    public FileProcessingResult processFile(Long operationId, Class<?> entityClass, int chunkSize)
            throws FileOperationException {
        FileOperation operation = findOperationOrThrow(operationId);
        FileOperationMetadata metadata = findMetadataOrThrow(operationId);

        updateOperationStatus(operation, OperationStatus.PROCESSING);

        Path filePath = resolveFilePath(metadata);

        // Для демонстрации - показываем сопоставление полей
        displayFieldDescriptions(entityClass);

        // Здесь будет реализация обработки файла в соответствии с типом операции
        return createSuccessResult(operationId);
    }

    /**
     * Находит операцию по идентификатору или выбрасывает исключение.
     *
     * @param operationId Идентификатор операции
     * @return Объект операции
     * @throws FileOperationException Если операция не найдена
     */
    private FileOperation findOperationOrThrow(Long operationId) throws FileOperationException {
        return fileOperationRepository.findById(operationId)
                .orElseThrow(() -> new FileOperationException(
                        "Операция с ID " + operationId + " не найдена"));
    }

    /**
     * Находит метаданные операции или выбрасывает исключение.
     *
     * @param operationId Идентификатор операции
     * @return Метаданные операции
     * @throws FileOperationException Если метаданные не найдены
     */
    private FileOperationMetadata findMetadataOrThrow(Long operationId) throws FileOperationException {
        FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
        if (metadata == null) {
            throw new FileOperationException("Метаданные операции с ID " + operationId + " не найдены");
        }
        return metadata;
    }

    /**
     * Обновляет статус операции.
     *
     * @param operation Операция
     * @param status    Новый статус
     */
    private void updateOperationStatus(FileOperation operation, OperationStatus status) {
        operation.setStatus(status);
        fileOperationRepository.save(operation);

        // Обновляем статус в кеше
        activeOperations.put(operation.getId(),
                new FileOperationStatus(operation.getId(), 0, 0, status));
    }

    /**
     * Разрешает путь к файлу из метаданных.
     *
     * @param metadata Метаданные операции
     * @return Путь к файлу
     * @throws FileOperationException Если файл не найден
     */
    private Path resolveFilePath(FileOperationMetadata metadata) throws FileOperationException {
        Path filePath = pathResolver.resolveRelativePath(metadata.getSourceFilePath());
        if (filePath == null || !Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + metadata.getSourceFilePath());
        }
        return filePath;
    }

    /**
     * Отображает сопоставление полей сущности с их описаниями.
     *
     * @param entityClass Класс сущности
     */
    private void displayFieldDescriptions(Class<?> entityClass) {
        Map<String, String> fieldDescriptions = FieldDescriptionUtils.getFieldDescriptions(entityClass);
        log.debug("Описания полей для {}: {}", entityClass.getSimpleName(), fieldDescriptions);
    }

    /**
     * Создает успешный результат обработки файла.
     *
     * @param operationId Идентификатор операции
     * @return Результат обработки
     */
    private FileProcessingResult createSuccessResult(Long operationId) {
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);
        result.setStatus(OperationStatus.COMPLETED);
        result.setMessage("Операция успешно завершена");
        return result;
    }

    /**
     * Получает информацию о статусе операции.
     *
     * @param operationId Идентификатор операции
     * @return Статус операции
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
                    int processedRecords = getProcessedRecordsCount(metadata);
                    int totalRecords = getTotalRecordsCount(metadata);
                    return new FileOperationStatus(operationId, processedRecords, totalRecords, operation.getStatus());
                })
                .orElse(new FileOperationStatus(operationId, 0, 0, OperationStatus.FAILED));
    }

    /**
     * Получает количество обработанных записей из метаданных.
     *
     * @param metadata Метаданные операции
     * @return Количество обработанных записей
     */
    private int getProcessedRecordsCount(FileOperationMetadata metadata) {
        return metadata != null && metadata.getProcessedRecords() != null
                ? metadata.getProcessedRecords() : 0;
    }

    /**
     * Получает общее количество записей из метаданных.
     *
     * @param metadata Метаданные операции
     * @return Общее количество записей
     */
    private int getTotalRecordsCount(FileOperationMetadata metadata) {
        return metadata != null && metadata.getTotalRecords() != null
                ? metadata.getTotalRecords() : 0;
    }

    /**
     * Отменяет операцию обработки файла.
     *
     * @param operationId Идентификатор операции
     * @return true, если операция успешно отменена
     */
    @Transactional
    public boolean cancelOperation(Long operationId) {
        // Отмечаем операцию как отмененную
        canceledOperations.add(operationId);

        // Обновляем статус в БД
        return fileOperationRepository.findById(operationId)
                .map(operation -> {
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
     * Проверяет, была ли отменена операция.
     *
     * @param operationId Идентификатор операции
     * @return true, если операция была отменена
     */
    public boolean isOperationCanceled(Long operationId) {
        return canceledOperations.contains(operationId);
    }

    /**
     * Обновляет прогресс обработки файла.
     *
     * @param operationId      Идентификатор операции
     * @param processedRecords Количество обработанных записей
     * @param totalRecords     Общее количество записей
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
        if (shouldUpdateProgressInDatabase(processedRecords, totalRecords)) {
            updateProgressInDb(operationId, processedRecords, totalRecords);
        }
    }

    /**
     * Определяет, нужно ли обновить прогресс в базе данных.
     *
     * @param processedRecords Количество обработанных записей
     * @param totalRecords     Общее количество записей
     * @return true, если нужно обновить прогресс
     */
    private boolean shouldUpdateProgressInDatabase(int processedRecords, int totalRecords) {
        return processedRecords % PROGRESS_UPDATE_THRESHOLD == 0 ||
                (totalRecords > 0 && (double) processedRecords / totalRecords >= PROGRESS_PERCENTAGE_UPDATE);
    }

    /**
     * Обновляет прогресс обработки файла в БД.
     *
     * @param operationId      Идентификатор операции
     * @param processedRecords Количество обработанных записей
     * @param totalRecords     Общее количество записей
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
     * Завершает операцию обработки файла.
     *
     * @param operationId Идентификатор операции
     * @param status      Статус завершения
     * @param message     Сообщение о результате
     * @param recordCount Количество обработанных записей
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
     * Вычисляет хеш содержимого файла.
     *
     * @param file Файл для вычисления хеша
     * @return Строка с хешем файла
     * @throws FileOperationException Если произошла ошибка при чтении файла
     */
    private String calculateFileHash(MultipartFile file) throws FileOperationException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return convertHashToHexString(hash);
        } catch (Exception e) {
            log.error("Ошибка при вычислении хеша файла: {}", e.getMessage());
            throw new FileOperationException("Ошибка при вычислении хеша файла: " + e.getMessage());
        }
    }

    /**
     * Преобразует байтовый массив хеша в шестнадцатеричную строку.
     *
     * @param hash Байтовый массив хеша
     * @return Шестнадцатеричная строка
     */
    private String convertHashToHexString(byte[] hash) {
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
     * Класс для хранения статуса операции обработки файла.
     */
    public static class FileOperationStatus {
        private final Long operationId;
        private final int processedRecords;
        private final int totalRecords;
        private final OperationStatus status;
        private final int progressPercent;

        /**
         * Создает новый статус операции.
         *
         * @param operationId      Идентификатор операции
         * @param processedRecords Количество обработанных записей
         * @param totalRecords     Общее количество записей
         * @param status           Статус операции
         */
        public FileOperationStatus(Long operationId, int processedRecords, int totalRecords, OperationStatus status) {
            this.operationId = operationId;
            this.processedRecords = processedRecords;
            this.totalRecords = totalRecords;
            this.status = status;

            // Вычисляем процент выполнения
            this.progressPercent = calculateProgressPercentage(processedRecords, totalRecords);
        }

        /**
         * Вычисляет процент выполнения операции.
         *
         * @param processed Количество обработанных записей
         * @param total     Общее количество записей
         * @return Процент выполнения (0-100)
         */
        private int calculateProgressPercentage(int processed, int total) {
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
     * Класс для хранения результата обработки файла.
     */
    public static class FileProcessingResult {
        private Long operationId;
        private OperationStatus status;
        private String message;
        private int processedRecords;
        private int successRecords;
        private int failedRecords;
        private List<String> errors;

        /**
         * Создает новый результат обработки файла.
         */
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