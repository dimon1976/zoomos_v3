package my.java.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для отправки уведомлений о прогрессе импорта через WebSocket
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImportProgressNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Отправляет уведомление о прогрессе операции
     */
    public void notifyProgress(Long operationId, int progress, int processed, int total) {
        Map<String, Object> message = new HashMap<>();
        message.put("operationId", operationId);
        message.put("progress", progress);
        message.put("processed", processed);
        message.put("total", total);
        message.put("status", "PROCESSING");

        String destination = "/topic/import-progress/" + operationId;
        messagingTemplate.convertAndSend(destination, message);

        log.debug("Отправлено уведомление о прогрессе операции {}: {}%", operationId, progress);
    }

    /**
     * Отправляет уведомление о завершении операции
     */
    public void notifyCompletion(Long operationId, int successCount, int errorCount) {
        Map<String, Object> message = new HashMap<>();
        message.put("operationId", operationId);
        message.put("status", "COMPLETED");
        message.put("successCount", successCount);
        message.put("errorCount", errorCount);
        message.put("progress", 100);

        String destination = "/topic/import-progress/" + operationId;
        messagingTemplate.convertAndSend(destination, message);

        log.info("Отправлено уведомление о завершении операции {}", operationId);
    }

    /**
     * Отправляет уведомление об ошибке
     */
    public void notifyError(Long operationId, String errorMessage) {
        Map<String, Object> message = new HashMap<>();
        message.put("operationId", operationId);
        message.put("status", "FAILED");
        message.put("error", errorMessage);

        String destination = "/topic/import-progress/" + operationId;
        messagingTemplate.convertAndSend(destination, message);

        log.error("Отправлено уведомление об ошибке операции {}: {}", operationId, errorMessage);
    }

    /**
     * Отправляет уведомление о начале операции
     */
    public void notifyStart(Long operationId) {
        Map<String, Object> message = new HashMap<>();
        message.put("operationId", operationId);
        message.put("status", "STARTED");
        message.put("progress", 0);

        String destination = "/topic/import-progress/" + operationId;
        messagingTemplate.convertAndSend(destination, message);

        log.info("Отправлено уведомление о начале операции {}", operationId);
    }
}