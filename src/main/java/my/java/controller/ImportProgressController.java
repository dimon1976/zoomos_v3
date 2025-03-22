package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.model.FileOperation;
import my.java.service.client.ClientService;
import my.java.service.file.importer.FileImportService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для отслеживания прогресса импорта файлов через WebSocket
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ImportProgressController {

    private final FileImportService fileImportService;
    private final ClientService clientService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Отображение страницы прогресса импорта
     */
    @GetMapping("/import/progress/{operationId}")
    public String showProgressPage(@PathVariable Long operationId, Model model) {
        log.debug("GET request to show import progress page for operation: {}", operationId);

        try {
            // Получаем информацию об операции
            FileOperationDto operation = fileImportService.getImportStatus(operationId);
            model.addAttribute("operation", operation);

            // Получаем информацию о клиенте
            clientService.getClientById(operation.getClientId())
                    .ifPresent(client -> model.addAttribute("client", client));

            return "import/progress";
        } catch (Exception e) {
            log.error("Error showing import progress page: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при получении информации о прогрессе импорта: " + e.getMessage());
            return "error/general";
        }
    }

    /**
     * WebSocket эндпоинт для получения обновлений о прогрессе
     * Клиенты подписываются на /topic/import-progress/{operationId}
     */
    @MessageMapping("/import-progress")
    public void getProgressUpdate(@Payload String message) {
        try {
            // Извлекаем ID операции из сообщения
            Long operationId = Long.parseLong(message);
            log.debug("Received WebSocket request for import progress update: operation #{}", operationId);

            // Получаем информацию о прогрессе
            FileOperationDto operation = fileImportService.getImportStatus(operationId);

            // Формируем ответ
            Map<String, Object> response = createProgressResponse(operation);

            // Отправляем обновление прогресса конкретному клиенту
            sendProgressUpdate(operationId, response);

            log.debug("Sent progress update for operation #{}: progress={}%, processed={}, total={}, status={}",
                    operationId,
                    operation.getProcessingProgress(),
                    operation.getProcessedRecords(),
                    operation.getTotalRecords(),
                    operation.getStatus());
        } catch (Exception e) {
            log.error("Error getting import progress update: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", true);
            errorResponse.put("message", e.getMessage());

            // Отправляем ответ об ошибке
            messagingTemplate.convertAndSend("/topic/import-progress", errorResponse);
        }
    }

    /**
     * Создает объект ответа с информацией о прогрессе
     *
     * @param operation информация об операции
     * @return объект ответа
     */
    private Map<String, Object> createProgressResponse(FileOperationDto operation) {
        Map<String, Object> response = new HashMap<>();

        // Базовая информация
        response.put("operationId", operation.getId());
        response.put("status", operation.getStatus().toString());

        // Информация о прогрессе
        response.put("progress", operation.getProcessingProgress() != null ? operation.getProcessingProgress() : 0);
        response.put("processedRecords", operation.getProcessedRecords() != null ? operation.getProcessedRecords() : 0);
        response.put("totalRecords", operation.getTotalRecords() != null ? operation.getTotalRecords() : 0);

        // Флаги состояния
        boolean isCompleted = operation.getStatus() == FileOperation.OperationStatus.COMPLETED ||
                operation.getStatus() == FileOperation.OperationStatus.FAILED;

        response.put("completed", isCompleted);
        response.put("successful", operation.getStatus() == FileOperation.OperationStatus.COMPLETED);

        // Дополнительная информация
        response.put("errorMessage", operation.getErrorMessage());
        response.put("startedAt", operation.getStartedAt());
        response.put("completedAt", operation.getCompletedAt());
        response.put("duration", operation.getDuration());

        // Расчет оставшегося времени (если операция в процессе)
        if (operation.getStatus() == FileOperation.OperationStatus.PROCESSING &&
                operation.getProcessingProgress() != null &&
                operation.getProcessingProgress() > 0 &&
                operation.getStartedAt() != null &&
                operation.getProcessedRecords() != null &&
                operation.getProcessedRecords() > 0 &&
                operation.getTotalRecords() != null &&
                operation.getTotalRecords() > 0) {

            try {
                ZonedDateTime now = ZonedDateTime.now();
                long elapsedSeconds = now.toEpochSecond() - operation.getStartedAt().toEpochSecond();

                if (elapsedSeconds > 0) {
                    double recordsPerSecond = operation.getProcessedRecords() / (double) elapsedSeconds;
                    long remainingRecords = operation.getTotalRecords() - operation.getProcessedRecords();
                    long estimatedSecondsRemaining = (long) (remainingRecords / recordsPerSecond);

                    response.put("estimatedTimeRemaining", estimatedSecondsRemaining);

                    // Добавляем сообщение о прогрессе
                    String progressMsg = String.format(
                            "Обработано %d из %d записей (%d%%). Скорость: %.1f записей/сек",
                            operation.getProcessedRecords(),
                            operation.getTotalRecords(),
                            operation.getProcessingProgress(),
                            recordsPerSecond
                    );

                    response.put("message", progressMsg);
                }
            } catch (Exception e) {
                log.warn("Ошибка при расчете оставшегося времени: {}", e.getMessage());
            }
        }

        return response;
    }

    /**
     * Отправляет обновление прогресса через WebSocket
     *
     * @param operationId ID операции
     * @param update информация об обновлении
     */
    public void sendProgressUpdate(Long operationId, Map<String, Object> update) {
        try {
            log.debug("Отправка WebSocket обновления для операции #{}", operationId);

            // Отправляем обновление для конкретной операции
            messagingTemplate.convertAndSend("/topic/import-progress/" + operationId, update);

            // Также отправляем в общий канал
            messagingTemplate.convertAndSend("/topic/import-progress", update);
        } catch (Exception e) {
            log.error("Ошибка при отправке WebSocket-обновления: {}", e.getMessage(), e);
        }
    }
}