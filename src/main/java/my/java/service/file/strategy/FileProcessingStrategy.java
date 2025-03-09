package my.java.service.file.strategy;

import my.java.service.file.FileProcessingService.FileProcessingResult;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для стратегий обработки файлов
 */
public interface FileProcessingStrategy {

    /**
     * Обрабатывает чанк данных в соответствии со стратегией
     *
     * @param dataChunk чанк данных из файла
     * @param operationId идентификатор операции
     * @param params параметры стратегии (может быть null)
     * @return результат обработки чанка
     */
    ChunkProcessingResult processChunk(List<Map<String, String>> dataChunk, Long operationId, Map<String, Object> params);

    /**
     * Завершает обработку файла
     *
     * @param operationId идентификатор операции
     * @param params параметры стратегии (может быть null)
     * @return результат обработки файла
     */
    FileProcessingResult finishProcessing(Long operationId, Map<String, Object> params);

    /**
     * Откатывает изменения в случае ошибки
     *
     * @param operationId идентификатор операции
     * @param params параметры стратегии (может быть null)
     */
    void rollback(Long operationId, Map<String, Object> params);

    /**
     * Проверяет валидность заголовков файла
     *
     * @param headers заголовки файла
     * @param params параметры стратегии (может быть null)
     * @return результат валидации
     */
    ValidationResult validateHeaders(List<String> headers, Map<String, Object> params);

    /**
     * Возвращает название стратегии
     *
     * @return название стратегии
     */
    String getName();

    /**
     * Возвращает описание стратегии
     *
     * @return описание стратегии
     */
    String getDescription();

    /**
     * Результат обработки чанка данных
     */
    class ChunkProcessingResult {
        private int processedRecords;
        private int successRecords;
        private int failedRecords;
        private List<String> errors;
        private boolean continueProcessing;

        public ChunkProcessingResult(int processedRecords, int successRecords, int failedRecords,
                                     List<String> errors, boolean continueProcessing) {
            this.processedRecords = processedRecords;
            this.successRecords = successRecords;
            this.failedRecords = failedRecords;
            this.errors = errors;
            this.continueProcessing = continueProcessing;
        }

        public int getProcessedRecords() {
            return processedRecords;
        }

        public int getSuccessRecords() {
            return successRecords;
        }

        public int getFailedRecords() {
            return failedRecords;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean isContinueProcessing() {
            return continueProcessing;
        }
    }

    /**
     * Результат валидации заголовков файла
     */
    class ValidationResult {
        private boolean valid;
        private List<String> missingHeaders;
        private List<String> errors;

        public ValidationResult(boolean valid, List<String> missingHeaders, List<String> errors) {
            this.valid = valid;
            this.missingHeaders = missingHeaders;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMissingHeaders() {
            return missingHeaders;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}