package my.java.service.file;

import lombok.Builder;
import lombok.Data;
import my.java.model.FileOperation.OperationStatus;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Класс для хранения дополнительных данных о файловых операциях
 */
@Data
@Builder
public class FileOperationMetadata {

    private Long operationId;
    private String fileHash;
    private Long fileSize;
    private String sourceFilePath;
    private String resultFilePath;
    private Integer totalRecords;
    private Integer processedRecords;
    private Integer processingProgress;
    private Long fieldMappingId;
    private Long strategyId;

    // Дополнительные параметры операции
    private Map<String, Object> additionalParams;

    // Статический кеш для хранения метаданных всех активных операций
    private static final Map<Long, FileOperationMetadata> METADATA_CACHE = new ConcurrentHashMap<>();

    /**
     * Создает метаданные операции
     *
     * @param operationId ID операции
     * @return метаданные операции
     */
    public static FileOperationMetadata create(Long operationId) {
        FileOperationMetadata metadata = FileOperationMetadata.builder()
                .operationId(operationId)
                .additionalParams(new HashMap<>())
                .processingProgress(0)
                .processedRecords(0)
                .build();

        METADATA_CACHE.put(operationId, metadata);
        return metadata;
    }

    /**
     * Получает метаданные операции
     *
     * @param operationId ID операции
     * @return метаданные операции или null, если не найдены
     */
    public static FileOperationMetadata get(Long operationId) {
        return METADATA_CACHE.get(operationId);
    }

    /**
     * Удаляет метаданные операции
     *
     * @param operationId ID операции
     */
    public static void remove(Long operationId) {
        METADATA_CACHE.remove(operationId);
    }

    /**
     * Обновляет прогресс обработки
     *
     * @param processedRecords количество обработанных записей
     * @param totalRecords общее количество записей
     */
    public void updateProgress(int processedRecords, int totalRecords) {
        this.processedRecords = processedRecords;
        this.totalRecords = totalRecords;

        if (totalRecords > 0) {
            this.processingProgress = (int) (((double) processedRecords / totalRecords) * 100);
        } else {
            this.processingProgress = 0;
        }
    }

    /**
     * Добавляет дополнительный параметр
     *
     * @param key ключ параметра
     * @param value значение параметра
     */
    public void addParam(String key, Object value) {
        if (additionalParams == null) {
            additionalParams = new HashMap<>();
        }
        additionalParams.put(key, value);
    }

    /**
     * Получает дополнительный параметр
     *
     * @param key ключ параметра
     * @return значение параметра или null, если не найден
     */
    public Object getParam(String key) {
        return additionalParams != null ? additionalParams.get(key) : null;
    }
}