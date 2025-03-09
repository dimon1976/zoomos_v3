package my.java.service.file.strategy;

import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation.OperationStatus;
import my.java.service.file.FileProcessingService.FileProcessingResult;

import java.util.*;

/**
 * Абстрактная реализация стратегии обработки файлов
 */
@Slf4j
public abstract class AbstractFileProcessingStrategy implements FileProcessingStrategy {

    // Счетчики для отслеживания статистики
    protected final Map<Long, OperationStats> operationStats = new HashMap<>();

    /**
     * Инициализирует статистику для операции
     *
     * @param operationId идентификатор операции
     */
    protected void initializeStats(Long operationId) {
        operationStats.put(operationId, new OperationStats());
    }

    /**
     * Обновляет статистику операции
     *
     * @param operationId идентификатор операции
     * @param chunkResult результат обработки чанка
     */
    protected void updateStats(Long operationId, ChunkProcessingResult chunkResult) {
        OperationStats stats = operationStats.computeIfAbsent(operationId, k -> new OperationStats());

        stats.processedRecords += chunkResult.getProcessedRecords();
        stats.successRecords += chunkResult.getSuccessRecords();
        stats.failedRecords += chunkResult.getFailedRecords();
        stats.errors.addAll(chunkResult.getErrors());
    }

    /**
     * Получает статистику операции
     *
     * @param operationId идентификатор операции
     * @return статистика операции
     */
    protected OperationStats getStats(Long operationId) {
        return operationStats.getOrDefault(operationId, new OperationStats());
    }

    /**
     * Очищает статистику операции
     *
     * @param operationId идентификатор операции
     */
    protected void clearStats(Long operationId) {
        operationStats.remove(operationId);
    }

    @Override
    public FileProcessingResult finishProcessing(Long operationId, Map<String, Object> params) {
        // Получаем статистику операции
        OperationStats stats = getStats(operationId);

        // Создаем результат
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);

        // Устанавливаем статус в зависимости от наличия ошибок
        if (stats.failedRecords > 0) {
            result.setStatus(OperationStatus.COMPLETED);
            result.setMessage("Обработка завершена с ошибками: " + stats.failedRecords + " из " + stats.processedRecords);
        } else {
            result.setStatus(OperationStatus.COMPLETED);
            result.setMessage("Обработка успешно завершена");
        }

        // Устанавливаем счетчики
        result.setProcessedRecords(stats.processedRecords);
        result.setSuccessRecords(stats.successRecords);
        result.setFailedRecords(stats.failedRecords);

        // Устанавливаем ошибки (ограничиваем количество для экономии памяти)
        if (!stats.errors.isEmpty()) {
            List<String> limitedErrors = stats.errors.size() > 100
                    ? stats.errors.subList(0, 100)
                    : stats.errors;
            result.setErrors(limitedErrors);

            if (stats.errors.size() > 100) {
                result.addError("... и еще " + (stats.errors.size() - 100) + " ошибок");
            }
        }

        // Очищаем статистику
        clearStats(operationId);

        return result;
    }

    @Override
    public void rollback(Long operationId, Map<String, Object> params) {
        // По умолчанию просто очищаем статистику
        clearStats(operationId);
    }

    @Override
    public ValidationResult validateHeaders(List<String> headers, Map<String, Object> params) {
        // Получаем ожидаемые заголовки
        Set<String> requiredHeaders = getRequiredHeaders(params);

        if (requiredHeaders.isEmpty()) {
            // Если нет обязательных заголовков, считаем валидацию успешной
            return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
        }

        // Проверяем наличие обязательных заголовков
        List<String> missingHeaders = new ArrayList<>();
        for (String requiredHeader : requiredHeaders) {
            if (!headers.contains(requiredHeader)) {
                missingHeaders.add(requiredHeader);
            }
        }

        boolean valid = missingHeaders.isEmpty();
        List<String> errors = new ArrayList<>();

        if (!valid) {
            errors.add("Отсутствуют обязательные заголовки: " + String.join(", ", missingHeaders));
        }

        return new ValidationResult(valid, missingHeaders, errors);
    }

    /**
     * Получает набор обязательных заголовков
     *
     * @param params параметры стратегии
     * @return набор обязательных заголовков
     */
    protected abstract Set<String> getRequiredHeaders(Map<String, Object> params);

    /**
     * Класс для хранения статистики операции
     */
    protected static class OperationStats {
        int processedRecords;
        int successRecords;
        int failedRecords;
        List<String> errors;

        public OperationStats() {
            this.processedRecords = 0;
            this.successRecords = 0;
            this.failedRecords = 0;
            this.errors = new ArrayList<>();
        }
    }
}