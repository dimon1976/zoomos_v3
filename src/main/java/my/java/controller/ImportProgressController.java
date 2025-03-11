// src/main/java/my/java/controller/ImportProgressController.java
package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.service.client.ClientService;
import my.java.service.file.importer.FileImportService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
     */
    @MessageMapping("/import-progress")
    @SendTo("/topic/import-progress")
    public Map<String, Object> getProgressUpdate(String message) {
        try {
            // Извлекаем ID операции из сообщения
            Long operationId = Long.parseLong(message);

            // Получаем информацию о прогрессе
            FileOperationDto operation = fileImportService.getImportStatus(operationId);

            // Формируем ответ
            Map<String, Object> response = new HashMap<>();
            response.put("operationId", operationId);
            response.put("status", operation.getStatus().toString());
//            response.put("progress", operation.getProcessingProgress() != null ? operation.getProcessingProgress() : 0);
//            response.put("processedRecords", operation.getProcessedRecords() != null ? operation.getProcessedRecords() : 0);
//            response.put("totalRecords", operation.getTotalRecords() != null ? operation.getTotalRecords() : 0);
            response.put("completed", operation.getStatus() == my.java.model.FileOperation.OperationStatus.COMPLETED ||
                    operation.getStatus() == my.java.model.FileOperation.OperationStatus.FAILED);
            response.put("successful", operation.getStatus() == my.java.model.FileOperation.OperationStatus.COMPLETED);
            response.put("errorMessage", operation.getErrorMessage());

            return response;
        } catch (Exception e) {
            log.error("Error getting import progress update: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", true);
            errorResponse.put("message", e.getMessage());

            return errorResponse;
        }
    }

    /**
     * Отправляет обновление прогресса через WebSocket
     *
     * @param operationId ID операции
     * @param update информация об обновлении
     */
    public void sendProgressUpdate(Long operationId, Map<String, Object> update) {
        messagingTemplate.convertAndSend("/topic/import-progress/" + operationId, update);
    }
}