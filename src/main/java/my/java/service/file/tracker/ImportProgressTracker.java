// src/main/java/my/java/service/file/tracker/ImportProgressTracker.java
package my.java.service.file.tracker;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Компонент для отслеживания прогресса операций импорта файлов.
 * Предоставляет методы для обновления и получения информации о прогрессе.
 */
@Component
@Slf4j
@NoArgsConstructor
public class ImportProgressTracker {

    protected FileOperationRepository fileOperationRepository;

    // Карта с информацией о прогрессе для каждой операции
    final Map<Long, ProgressInfo> progressMap = new ConcurrentHashMap<>();

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param fileOperationRepository репозиторий операций с файлами
     */
    public ImportProgressTracker(FileOperationRepository fileOperationRepository) {
        this.fileOperationRepository = fileOperationRepository;
    }

    /**
     * Инициализирует отслеживание прогресса для новой операции.
     *
     * @param operationId  ID операции
     * @param totalRecords общее количество записей для обработки
     * @return информация о прогрессе
     */
    public ProgressInfo initProgress(Long operationId, int totalRecords) {
        log.debug("Инициализация отслеживания прогресса для операции #{}, всего записей: {}",
                operationId, totalRecords);

        ProgressInfo progressInfo = new ProgressInfo(totalRecords);
        progressMap.put(operationId, progressInfo);

        // Обновляем информацию в БД
        updateOperationInDb(operationId, 0, totalRecords, 0);

        return progressInfo;
    }

    /**
     * Обновляет прогресс операции.
     *
     * @param operationId      ID операции
     * @param processedRecords количество обработанных записей
     * @return текущий прогресс в процентах (0-100)
     */
    public int updateProgress(Long operationId, int processedRecords) {
        ProgressInfo progressInfo = getProgressInfoOrNull(operationId);

        if (progressInfo == null) {
            log.warn("Попытка обновить прогресс для неотслеживаемой операции #{}", operationId);
            return 0;
        }

        // Обновляем количество обработанных записей
        progressInfo.setProcessedRecords(processedRecords);

        // Вычисляем процент выполнения
        int progressPercent = calculateProgressPercent(progressInfo);

        // Если процент изменился, обновляем информацию в БД
        if (shouldUpdateDatabase(progressInfo, progressPercent)) {
            progressInfo.setLastReportedPercent(progressPercent);
            updateOperationInDb(operationId, processedRecords, progressInfo.getTotalRecords(), progressPercent);
        }

        return progressPercent;
    }

    /**
     * Увеличивает счетчик обработанных записей на указанное значение.
     *
     * @param operationId ID операции
     * @param incrementBy значение, на которое нужно увеличить счетчик
     * @return текущий прогресс в процентах (0-100)
     */
    public int incrementProgress(Long operationId, int incrementBy) {
        ProgressInfo progressInfo = getProgressInfoOrNull(operationId);

        if (progressInfo == null) {
            log.warn("Попытка обновить прогресс для неотслеживаемой операции #{}", operationId);
            return 0;
        }

        // Увеличиваем счетчик обработанных записей
        int newProcessedRecords = progressInfo.getProcessedRecords().addAndGet(incrementBy);

        // Вычисляем процент выполнения
        int progressPercent = calculateProgressPercent(progressInfo);

        // Если процент изменился или прошло достаточно времени с последнего обновления, обновляем информацию в БД
        if (shouldUpdateDatabase(progressInfo, progressPercent) ||
                isTimeThresholdExceeded(progressInfo)) {

            updateDatabaseAndResetTimers(operationId, progressInfo, newProcessedRecords, progressPercent);
        }

        return progressPercent;
    }

    /**
     * Получает текущую информацию о прогрессе.
     *
     * @param operationId ID операции
     * @return информация о прогрессе или null, если операция не отслеживается
     */
    public ProgressInfo getProgressInfo(Long operationId) {
        return progressMap.get(operationId);
    }

    /**
     * Получает текущий прогресс в процентах.
     *
     * @param operationId ID операции
     * @return прогресс в процентах (0-100) или -1, если операция не отслеживается
     */
    public int getProgressPercent(Long operationId) {
        ProgressInfo progressInfo = progressMap.get(operationId);

        if (progressInfo == null) {
            return -1;
        }

        return calculateProgressPercent(progressInfo);
    }

    /**
     * Завершает отслеживание прогресса для операции.
     *
     * @param operationId       ID операции
     * @param successful        true, если операция завершилась успешно
     * @param totalProcessed    общее количество обработанных записей
     * @param successfulRecords количество успешно обработанных записей
     * @param errorMessage      сообщение об ошибке (если операция не успешна)
     */
    public void completeProgress(Long operationId, boolean successful, int totalProcessed,
                                 int successfulRecords, String errorMessage) {
        ProgressInfo progressInfo = progressMap.get(operationId);

        if (progressInfo == null) {
            log.warn("Попытка завершить прогресс для неотслеживаемой операции #{}", operationId);
            return;
        }

        // Обновляем информацию о завершении в БД
        try {
            updateCompletionStatus(operationId, successful, totalProcessed,
                    progressInfo.getTotalRecords(), successfulRecords, errorMessage);

            log.info("Операция #{} {} обработано: {}, успешно: {}",
                    operationId,
                    successful ? "успешно завершена," : "завершена с ошибкой,",
                    totalProcessed,
                    successfulRecords);
        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса операции #{}: {}", operationId, e.getMessage());
        } finally {
            // Удаляем информацию о прогрессе из карты
            progressMap.remove(operationId);
        }
    }

    /**
     * Вычисляет процент выполнения операции.
     *
     * @param progressInfo информация о прогрессе
     * @return процент выполнения (0-100)
     */
    protected int calculateProgressPercent(ProgressInfo progressInfo) {
        int totalRecords = progressInfo.getTotalRecords();
        int processedRecords = progressInfo.getProcessedRecords().get();

        if (totalRecords <= 0) {
            return 0;
        }

        return Math.min(100, (int) ((processedRecords * 100.0) / totalRecords));
    }

    /**
     * Определяет, нужно ли обновлять информацию в БД.
     *
     * @param progressInfo           информация о прогрессе
     * @param currentProgressPercent текущий процент прогресса
     * @return true, если нужно обновить информацию в БД
     */
    private boolean shouldUpdateDatabase(ProgressInfo progressInfo, int currentProgressPercent) {
        return currentProgressPercent != progressInfo.getLastReportedPercent();
    }

    /**
     * Проверяет, прошло ли достаточно времени с последнего обновления БД.
     *
     * @param progressInfo информация о прогрессе
     * @return true, если пора обновить данные в БД
     */
    private boolean isTimeThresholdExceeded(ProgressInfo progressInfo) {
        // Обновляем БД не чаще чем раз в секунду
        long timeSinceLastUpdate = System.currentTimeMillis() - progressInfo.getLastDbUpdateTime();
        return timeSinceLastUpdate > 1000;
    }

    /**
     * Обновляет информацию в БД и сбрасывает таймеры.
     *
     * @param operationId      ID операции
     * @param progressInfo     информация о прогрессе
     * @param processedRecords количество обработанных записей
     * @param progressPercent  процент выполнения
     */
    private void updateDatabaseAndResetTimers(Long operationId, ProgressInfo progressInfo,
                                              int processedRecords, int progressPercent) {
        progressInfo.setLastReportedPercent(progressPercent);
        progressInfo.setLastDbUpdateTime(System.currentTimeMillis());
        updateOperationInDb(operationId, processedRecords, progressInfo.getTotalRecords(), progressPercent);
    }

    /**
     * Получает информацию о прогрессе операции (null-safe).
     *
     * @param operationId ID операции
     * @return информация о прогрессе или null, если операция не отслеживается
     */
    private ProgressInfo getProgressInfoOrNull(Long operationId) {
        return progressMap.get(operationId);
    }

    /**
     * Обновляет информацию об операции в БД.
     *
     * @param operationId      ID операции
     * @param processedRecords количество обработанных записей
     * @param totalRecords     общее количество записей
     * @param progressPercent  процент выполнения
     */
    protected void updateOperationInDb(Long operationId, int processedRecords, int totalRecords, int progressPercent) {
        try {
            FileOperation operation = findOperationById(operationId);
            updateOperationProgress(operation, processedRecords, totalRecords, progressPercent);
            saveOperation(operation);

            log.debug("Обновлен прогресс для операции #{}: {}% ({} из {})",
                    operationId, progressPercent, processedRecords, totalRecords);
        } catch (Exception e) {
            log.error("Ошибка при обновлении прогресса операции #{}: {}", operationId, e.getMessage());
        }
    }

    /**
     * Находит операцию по ID.
     *
     * @param operationId ID операции
     * @return найденная операция
     * @throws IllegalArgumentException если операция не найдена
     */
    private FileOperation findOperationById(Long operationId) {
        return fileOperationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Операция с ID " + operationId + " не найдена"));
    }

    /**
     * Обновляет информацию о прогрессе в объекте операции.
     *
     * @param operation        объект операции
     * @param processedRecords количество обработанных записей
     * @param totalRecords     общее количество записей
     * @param progressPercent  процент выполнения
     */
    private void updateOperationProgress(FileOperation operation, int processedRecords,
                                         int totalRecords, int progressPercent) {
        operation.setProcessedRecords(processedRecords);
        operation.setTotalRecords(totalRecords);
        operation.setProcessingProgress(progressPercent);
    }

    /**
     * Сохраняет операцию в репозитории.
     *
     * @param operation операция для сохранения
     */
    private void saveOperation(FileOperation operation) {
        fileOperationRepository.save(operation);
    }

    /**
     * Обновляет статус завершения операции.
     *
     * @param operationId       ID операции
     * @param successful        флаг успешности операции
     * @param totalProcessed    общее количество обработанных записей
     * @param totalRecords      общее количество записей
     * @param successfulRecords количество успешно обработанных записей
     * @param errorMessage      сообщение об ошибке
     */
    private void updateCompletionStatus(Long operationId, boolean successful, int totalProcessed,
                                        int totalRecords, int successfulRecords, String errorMessage) {
        FileOperation operation = findOperationById(operationId);

        if (successful) {
            operation.markAsCompleted(successfulRecords);
        } else {
            operation.markAsFailed(errorMessage);
        }

        // Устанавливаем окончательное значение прогресса
        operation.setProcessedRecords(totalProcessed);
        operation.setProcessingProgress(100);
        operation.setTotalRecords(totalRecords);

        saveOperation(operation);
    }
}