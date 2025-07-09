// src/main/java/my/java/controller/OperationStatusController.java
package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation;
import my.java.service.file.importer.ImportOrchestratorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для отображения статуса файловых операций
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class OperationStatusController {

    private final ImportOrchestratorService importOrchestratorService;

    /**
     * Отображение страницы статуса операции
     */
    @GetMapping("/operations/{operationId}/status")
    public String showOperationStatus(@PathVariable Long operationId,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        log.debug("GET request to show operation status: {}", operationId);

        try {
            FileOperation operation = importOrchestratorService.getOperationStatus(operationId);

            // Подготавливаем данные для отображения
            model.addAttribute("operation", operation);
            model.addAttribute("operationStatus", operation.getStatus());
            model.addAttribute("progressPercentage", operation.getProcessingProgress());

            // Добавляем ID клиента отдельно для безопасности
            if (operation.getClient() != null) {
                model.addAttribute("clientId", operation.getClient().getId());
                model.addAttribute("clientName", operation.getClient().getName());
            }

            // Форматируем даты для отображения
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            if (operation.getStartedAt() != null) {
                model.addAttribute("formattedStartedAt", operation.getStartedAt().format(formatter));
            }
            if (operation.getCompletedAt() != null) {
                model.addAttribute("formattedCompletedAt", operation.getCompletedAt().format(formatter));
            }

            // Добавляем вычисляемые поля для отображения
            model.addAttribute("statusClass", getStatusCssClass(operation.getStatus()));
            model.addAttribute("statusDisplay", getStatusDisplay(operation.getStatus()));
            model.addAttribute("operationTypeDisplay", getOperationTypeDisplay(operation.getOperationType()));
            model.addAttribute("duration", operation.getDuration());

            return "operations/status";

        } catch (Exception e) {
            log.error("Error loading operation status: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка загрузки статуса операции: " + e.getMessage());
            return "redirect:/";
        }
    }

    /**
     * AJAX endpoint для получения актуального статуса операции
     */
    @GetMapping("/api/operations/{operationId}/status")
    @ResponseBody
    public Map<String, Object> getOperationStatusJson(@PathVariable Long operationId) {
        log.debug("AJAX request for operation status: {}", operationId);

        Map<String, Object> response = new HashMap<>();

        try {
            FileOperation operation = importOrchestratorService.getOperationStatus(operationId);

            response.put("success", true);
            response.put("status", operation.getStatus().name());
            response.put("statusDisplay", getStatusDisplay(operation.getStatus()));
            response.put("progress", operation.getProcessingProgress());
            response.put("processedRecords", operation.getProcessedRecords());
            response.put("totalRecords", operation.getTotalRecords());
            response.put("errorMessage", operation.getErrorMessage());
            response.put("duration", operation.getDuration());

            // Добавляем информацию о завершении
            if (operation.getCompletedAt() != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
                response.put("completedAt", operation.getCompletedAt().format(formatter));
            }

        } catch (Exception e) {
            log.error("Error getting operation status via API: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * Получение CSS класса для статуса
     */
    private String getStatusCssClass(FileOperation.OperationStatus status) {
        switch (status) {
            case PENDING:
                return "badge bg-warning";
            case PROCESSING:
                return "badge bg-info";
            case COMPLETED:
                return "badge bg-success";
            case FAILED:
                return "badge bg-danger";
            default:
                return "badge bg-secondary";
        }
    }

    /**
     * Получение отображаемого названия статуса
     */
    private String getStatusDisplay(FileOperation.OperationStatus status) {
        switch (status) {
            case PENDING:
                return "Ожидает обработки";
            case PROCESSING:
                return "В обработке";
            case COMPLETED:
                return "Завершено";
            case FAILED:
                return "Ошибка";
            default:
                return "Неизвестно";
        }
    }

    /**
     * Получение отображаемого названия типа операции
     */
    private String getOperationTypeDisplay(FileOperation.OperationType operationType) {
        switch (operationType) {
            case IMPORT:
                return "Импорт";
            case EXPORT:
                return "Экспорт";
            case PROCESS:
                return "Обработка";
            default:
                return "Операция";
        }
    }
}