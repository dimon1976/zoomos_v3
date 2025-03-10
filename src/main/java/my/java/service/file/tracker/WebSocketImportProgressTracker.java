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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реализация отслеживания прогресса импорта с использованием WebSocket
 * для оповещения клиентов о прогрессе в реальном времени
 */
@Component
@Primary
@Slf4j
public class WebSocketImportProgressTracker extends ImportProgressTracker {


    private final SimpMessagingTemplate messagingTemplate;

    // Карта с информацией о прогрессе для каждой операции
    private final Map<Long, ProgressInfo> progressMap = new ConcurrentHashMap<>();

    public WebSocketImportProgressTracker(
            FileOperationRepository fileOperationRepository,
            SimpMessagingTemplate messagingTemplate) {
        super(fileOperationRepository);  // Вызываем конструктор базового класса
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
        ProgressInfo progressInfo = progressMap.get(operationId);

        if (progressInfo == null) {
            log.warn("Попытка обновить прогресс для неотслеживаемой операции #{}", operationId);
            return 0;
        }

        // Обновляем количество обработанных записей
        progressInfo.setProcessedRecords(processedRecords);

        // Вычисляем процент выполнения
        int progressPercent = calculateProgressPercent(progressInfo);

        // Если процент изменился, обновляем информацию в БД и отправляем обновление через WebSocket
        if (progressPercent != progressInfo.getLastReportedPercent()) {
            progressInfo.setLastReportedPercent(progressPercent);
            updateOperationInDb(operationId, processedRecords, progressInfo.getTotalRecords(), progressPercent);
            sendProgressUpdateViaWebSocket(operationId, processedRecords, progressInfo.getTotalRecords(), progressPercent);
        }

        return progressPercent;
    }

    @Override
    public int incrementProgress(Long operationId, int incrementBy) {
        ProgressInfo progressInfo = progressMap.get(operationId);

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
        if (progressPercent != progressInfo.getLastReportedPercent() ||
                shouldUpdateDatabase(progressInfo)) {
            progressInfo.setLastReportedPercent(progressPercent);
            progressInfo.setLastDbUpdateTime(System.currentTimeMillis());
            updateOperationInDb(operationId, newProcessedRecords, progressInfo.getTotalRecords(), progressPercent);
            sendProgressUpdateViaWebSocket(operationId, newProcessedRecords, progressInfo.getTotalRecords(), progressPercent);
        }

        return progressPercent;
    }

    @Override
    public void completeProgress(Long operationId, boolean successful, int totalProcessed,
                                 int successfulRecords, String errorMessage) {
        ProgressInfo progressInfo = progressMap.get(operationId);

        if (progressInfo == null) {
            log.warn("Попытка завершить прогресс для неотслеживаемой операции #{}", operationId);
            return;
        }

        // Обновляем информацию о завершении в БД
        try {
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция с ID " + operationId + " не найдена"));

            if (successful) {
                operation.markAsCompleted(successfulRecords);
            } else {
                operation.markAsFailed(errorMessage);
            }

            // Устанавливаем окончательное значение прогресса
            operation.setProcessedRecords(totalProcessed);
            operation.setProcessingProgress(100);
            operation.setTotalRecords(progressInfo.getTotalRecords());

            fileOperationRepository.save(operation);

            log.info("Операция #{} {} обработано: {}, успешно: {}",
                    operationId,
                    successful ? "успешно завершена," : "завершена с ошибкой,",
                    totalProcessed,
                    successfulRecords);

            // Отправляем финальное обновление через WebSocket
            sendCompletionUpdateViaWebSocket(operationId, successful, totalProcessed, successfulRecords, errorMessage);
        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса операции #{}: {}", operationId, e.getMessage());
        } finally {
            // Удаляем информацию о прогрессе из карты
            progressMap.remove(operationId);
        }
    }

    /**
     * Отправляет обновление прогресса через WebSocket
     */
    private void sendProgressUpdateViaWebSocket(Long operationId, int processedRecords, int totalRecords, int progressPercent) {
        String destination = "/topic/import-progress/" + operationId;

        Map<String, Object> progressUpdate = new HashMap<>();
        progressUpdate.put("operationId", operationId);
        progressUpdate.put("processedRecords", processedRecords);
        progressUpdate.put("totalRecords", totalRecords);
        progressUpdate.put("progress", progressPercent);
        progressUpdate.put("completed", false);

        messagingTemplate.convertAndSend(destination, progressUpdate);
    }

    /**
     * Отправляет уведомление о завершении через WebSocket
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

        messagingTemplate.convertAndSend(destination, completionUpdate);
    }

    // Остальные методы аналогичны базовому классу ImportProgressTracker
    protected int calculateProgressPercent(ProgressInfo progressInfo) {
        int totalRecords = progressInfo.getTotalRecords();
        int processedRecords = progressInfo.getProcessedRecords().get();

        if (totalRecords <= 0) {
            return 0;
        }

        return Math.min(100, (int) ((processedRecords * 100.0) / totalRecords));
    }

    private boolean shouldUpdateDatabase(ProgressInfo progressInfo) {
        // Обновляем БД не чаще чем раз в секунду
        long timeSinceLastUpdate = System.currentTimeMillis() - progressInfo.getLastDbUpdateTime();
        return timeSinceLastUpdate > 1000;
    }

    protected void updateOperationInDb(Long operationId, int processedRecords, int totalRecords, int progressPercent) {
        try {
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция с ID " + operationId + " не найдена"));

            operation.setProcessedRecords(processedRecords);
            operation.setTotalRecords(totalRecords);
            operation.setProcessingProgress(progressPercent);

            fileOperationRepository.save(operation);

            log.debug("Обновлен прогресс для операции #{}: {}% ({} из {})",
                    operationId, progressPercent, processedRecords, totalRecords);
        } catch (Exception e) {
            log.error("Ошибка при обновлении прогресса операции #{}: {}", operationId, e.getMessage());
        }
    }
}