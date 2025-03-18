// src/main/java/my/java/service/file/tracker/WebSocketImportProgressTracker.java
package my.java.service.file.tracker;

import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Реализация отслеживания прогресса импорта с использованием WebSocket
 * для оповещения клиентов о прогрессе в реальном времени
 */
@Component
@Primary
@Slf4j
public class WebSocketImportProgressTracker extends ImportProgressTracker {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Создает трекер с поддержкой WebSocket уведомлений
     *
     * @param fileOperationRepository репозиторий для операций с файлами
     * @param messagingTemplate шаблон для отправки WebSocket сообщений
     */
    public WebSocketImportProgressTracker(
            FileOperationRepository fileOperationRepository,
            SimpMessagingTemplate messagingTemplate) {
        super(fileOperationRepository);
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public ProgressInfo initProgress(Long operationId, int totalRecords) {
        log.debug("Инициализация отслеживания прогресса с WebSocket для операции #{}, всего записей: {}",
                operationId, totalRecords);

        // Вызываем метод родительского класса
        ProgressInfo progressInfo = super.initProgress(operationId, totalRecords);

        // Отправляем начальное сообщение через WebSocket
        sendProgressUpdateViaWebSocket(operationId, 0, totalRecords, 0);

        return progressInfo;
    }

    @Override
    public int updateProgress(Long operationId, int processedRecords) {
        ProgressInfo progressInfo = getProgressInfo(operationId);

        if (progressInfo == null) {
            log.warn("Попытка обновить прогресс для неотслеживаемой операции #{}", operationId);
            return 0;
        }

        // Обновляем количество обработанных записей
        progressInfo.setProcessedRecords(processedRecords);

        // Вычисляем процент выполнения
        int progressPercent = calculateProgressPercent(progressInfo);

        // Если процент изменился, обновляем информацию в БД и отправляем обновление через WebSocket
        if (shouldUpdateProgress(progressInfo, progressPercent)) {
            updateProgressStateWithWebSocket(operationId, progressInfo, processedRecords, progressPercent);
        }

        return progressPercent;
    }

    @Override
    public int incrementProgress(Long operationId, int incrementBy) {
        ProgressInfo progressInfo = getProgressInfo(operationId);

        if (progressInfo == null) {
            log.warn("Попытка обновить прогресс для неотслеживаемой операции #{}", operationId);
            return 0;
        }

        // Увеличиваем счетчик обработанных записей
        int newProcessedRecords = progressInfo.getProcessedRecords().addAndGet(incrementBy);

        // Вычисляем процент выполнения
        int progressPercent = calculateProgressPercent(progressInfo);

        // Если процент изменился или прошло достаточно времени с последнего обновления,
        // обновляем информацию в БД и отправляем обновление через WebSocket
        if (shouldUpdateProgress(progressInfo, progressPercent) ||
                isTimeThresholdExceeded(progressInfo)) {
            updateProgressStateWithWebSocket(operationId, progressInfo, newProcessedRecords, progressPercent);
        }

        return progressPercent;
    }

    @Override
    public void completeProgress(Long operationId, boolean successful, int totalProcessed,
                                 int successfulRecords, String errorMessage) {
        ProgressInfo progressInfo = getProgressInfo(operationId);

        if (progressInfo == null) {
            log.warn("Попытка завершить прогресс для неотслеживаемой операции #{}", operationId);
            return;
        }

        try {
            // Обновляем информацию о завершении в БД
            updateCompletionState(operationId, successful, totalProcessed,
                    progressInfo.getTotalRecords(), successfulRecords, errorMessage);

            // Отправляем финальное обновление через WebSocket
            sendCompletionUpdateViaWebSocket(operationId, successful, totalProcessed,
                    successfulRecords, errorMessage);

            log.info("Операция #{} {} обработано: {}, успешно: {}",
                    operationId,
                    successful ? "успешно завершена," : "завершена с ошибкой,",
                    totalProcessed,
                    successfulRecords);
        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса операции #{}: {}", operationId, e.getMessage());
        } finally {
            // Удаляем информацию о прогрессе из карты
            removeProgressInfo(operationId);
        }
    }

    /**
     * Определяет, нужно ли обновлять прогресс операции.
     *
     * @param progressInfo информация о прогрессе
     * @param currentProgressPercent текущий процент прогресса
     * @return true, если нужно обновить прогресс
     */
    private boolean shouldUpdateProgress(ProgressInfo progressInfo, int currentProgressPercent) {
        return currentProgressPercent != progressInfo.getLastReportedPercent();
    }

    /**
     * Определяет, прошло ли достаточно времени с последнего обновления.
     *
     * @param progressInfo информация о прогрессе
     * @return true, если пора обновить данные
     */
    private boolean isTimeThresholdExceeded(ProgressInfo progressInfo) {
        // Обновляем не чаще чем раз в секунду
        long timeSinceLastUpdate = System.currentTimeMillis() - progressInfo.getLastDbUpdateTime();
        return timeSinceLastUpdate > 1000;
    }

    /**
     * Обновляет состояние прогресса и отправляет WebSocket уведомление.
     *
     * @param operationId ID операции
     * @param progressInfo информация о прогрессе
     * @param processedRecords количество обработанных записей
     * @param progressPercent процент выполнения
     */
    private void updateProgressStateWithWebSocket(Long operationId, ProgressInfo progressInfo,
                                                  int processedRecords, int progressPercent) {
        progressInfo.setLastReportedPercent(progressPercent);
        progressInfo.setLastDbUpdateTime(System.currentTimeMillis());

        updateOperationInDb(operationId, processedRecords, progressInfo.getTotalRecords(), progressPercent);
        sendProgressUpdateViaWebSocket(operationId, processedRecords, progressInfo.getTotalRecords(), progressPercent);
    }

    /**
     * Обновляет состояние завершения операции в БД.
     *
     * @param operationId ID операции
     * @param successful флаг успешности операции
     * @param totalProcessed общее количество обработанных записей
     * @param totalRecords общее количество записей
     * @param successfulRecords количество успешно обработанных записей
     * @param errorMessage сообщение об ошибке
     */
    private void updateCompletionState(Long operationId, boolean successful, int totalProcessed,
                                       int totalRecords, int successfulRecords, String errorMessage) {
        try {
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
        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса операции #{}: {}", operationId, e.getMessage());
            throw e;
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
     * Сохраняет операцию в репозитории.
     *
     * @param operation операция для сохранения
     */
    private void saveOperation(FileOperation operation) {
        fileOperationRepository.save(operation);
    }

    /**
     * Удаляет информацию о прогрессе из карты.
     *
     * @param operationId ID операции
     */
    private void removeProgressInfo(Long operationId) {
        // Метод должен быть защищен от исключений, чтобы всегда очистить состояние
        try {
            super.progressMap.remove(operationId);
        } catch (Exception e) {
            log.error("Ошибка при удалении информации о прогрессе операции #{}: {}", operationId, e.getMessage());
        }
    }

    /**
     * Отправляет обновление прогресса через WebSocket
     *
     * @param operationId ID операции
     * @param processedRecords количество обработанных записей
     * @param totalRecords общее количество записей
     * @param progressPercent процент выполнения
     */
    private void sendProgressUpdateViaWebSocket(Long operationId, int processedRecords,
                                                int totalRecords, int progressPercent) {
        String destination = "/topic/import-progress/" + operationId;

        Map<String, Object> progressUpdate = new HashMap<>();
        progressUpdate.put("operationId", operationId);
        progressUpdate.put("processedRecords", processedRecords);
        progressUpdate.put("totalRecords", totalRecords);
        progressUpdate.put("progress", progressPercent);
        progressUpdate.put("completed", false);

        try {
            messagingTemplate.convertAndSend(destination, progressUpdate);
            log.debug("Отправлено WebSocket-обновление прогресса: операция #{}, прогресс: {}%",
                    operationId, progressPercent);
        } catch (Exception e) {
            log.error("Ошибка при отправке WebSocket-обновления прогресса для операции #{}: {}",
                    operationId, e.getMessage());
        }
    }

    /**
     * Отправляет уведомление о завершении через WebSocket
     *
     * @param operationId ID операции
     * @param successful флаг успешности операции
     * @param totalProcessed общее количество обработанных записей
     * @param successfulRecords количество успешно обработанных записей
     * @param errorMessage сообщение об ошибке
     */
    private void sendCompletionUpdateViaWebSocket(Long operationId, boolean successful,
                                                  int totalProcessed, int successfulRecords,
                                                  String errorMessage) {
        String destination = "/topic/import-progress/" + operationId;

        Map<String, Object> completionUpdate = new HashMap<>();
        completionUpdate.put("operationId", operationId);
        completionUpdate.put("processedRecords", totalProcessed);
        completionUpdate.put("successfulRecords", successfulRecords);
        completionUpdate.put("progress", 100);
        completionUpdate.put("completed", true);
        completionUpdate.put("successful", successful);
        completionUpdate.put("errorMessage", errorMessage);

        try {
            messagingTemplate.convertAndSend(destination, completionUpdate);
            log.debug("Отправлено WebSocket-уведомление о завершении: операция #{}, успешно: {}",
                    operationId, successful);
        } catch (Exception e) {
            log.error("Ошибка при отправке WebSocket-уведомления о завершении для операции #{}: {}",
                    operationId, e.getMessage());
        }
    }
}