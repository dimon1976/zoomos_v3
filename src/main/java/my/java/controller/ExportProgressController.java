package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.service.client.ClientService;
import my.java.service.file.exporter.FileExportService;
import my.java.service.file.exporter.tracker.ExportProgressTracker;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Контроллер для отслеживания прогресса экспорта через WebSocket
 * Путь: /java/my/java/controller/ExportProgressController.java
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ExportProgressController {

    private final FileExportService exportService;
    private final ClientService clientService;
    private final ExportProgressTracker progressTracker;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Показывает страницу прогресса экспорта
     */
    @GetMapping("/export/progress/{operationId}")
    public String showProgressPage(@PathVariable Long operationId, Model model) {
        log.debug("Запрос на отображение страницы прогресса экспорта для операции: {}", operationId);

        try {
            // Получаем информацию об операции
            FileOperationDto operation = exportService.getExportStatus(operationId);
            model.addAttribute("operation", operation);

            // Получаем клиента
            clientService.getClientById(operation.getClientId())
                    .ifPresent(client -> model.addAttribute("client", client));

            return "export/progress";
        } catch (Exception e) {
            log.error("Ошибка при отображении страницы прогресса экспорта: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при получении информации о прогрессе: " + e.getMessage());
            return "error/general";
        }
    }

    /**
     * WebSocket эндпоинт для получения обновлений о прогрессе экспорта
     * Клиент отправляет ID операции и подписывается на /topic/export-progress/{operationId}
     */
    @MessageMapping("/export-progress")
    public void getProgressUpdate(@Payload String message) {
        try {
            // Извлекаем ID операции из сообщения
            Long operationId = Long.parseLong(message);
            log.debug("Получен WebSocket запрос на обновление прогресса экспорта: операция #{}", operationId);

            // Получаем информацию о прогрессе
            Map<String, Object> progressInfo = progressTracker.getProgressInfo(operationId);

            // Если информации нет в кэше, получаем из БД
            if (progressInfo.isEmpty()) {
                FileOperationDto operation = exportService.getExportStatus(operationId);

                progressInfo.put("operationId", operation.getId());
                progressInfo.put("status", operation.getStatus().toString());
                progressInfo.put("progress", operation.getProcessingProgress());
                progressInfo.put("processed", operation.getProcessedRecords());
                progressInfo.put("total", operation.getTotalRecords());
                progressInfo.put("completedAt", operation.getCompletedAt());

                if (operation.getCompletedAt() != null) {
                    progressInfo.put("completed", true);
                    progressInfo.put("successful", "COMPLETED".equals(operation.getStatus().toString()));
                }
            }

            // Отправляем обновление прогресса
            messagingTemplate.convertAndSend("/topic/export-progress/" + operationId, progressInfo);

            log.debug("Отправлено обновление прогресса для операции #{}: прогресс={}%, обработано={}, всего={}",
                    operationId,
                    progressInfo.get("progress"),
                    progressInfo.get("processed"),
                    progressInfo.get("total"));

        } catch (Exception e) {
            log.error("Ошибка при получении обновления прогресса экспорта: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправляет обновление о прогрессе экспорта через WebSocket
     * Метод для вызова из других компонентов
     */
    public void sendProgressUpdate(Long operationId, Map<String, Object> progressInfo) {
        try {
            messagingTemplate.convertAndSend("/topic/export-progress/" + operationId, progressInfo);
            log.debug("Отправлено обновление прогресса экспорта для операции #{}", operationId);
        } catch (Exception e) {
            log.error("Ошибка при отправке обновления прогресса: {}", e.getMessage(), e);
        }
    }
}