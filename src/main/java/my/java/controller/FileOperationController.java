package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;

/**
 * Контроллер для работы с файловыми операциями
 */
@Controller
@RequestMapping("/operations")
@RequiredArgsConstructor
@Slf4j
public class FileOperationController {

    private final FileOperationRepository operationRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    /**
     * Страница статуса операции
     */
    @GetMapping("/{id}/status")
    public String showOperationStatus(@PathVariable Long id, Model model,
                                      RedirectAttributes redirectAttributes) {
        log.debug("GET request to show operation status for id: {}", id);

        return operationRepository.findByIdWithClient(id)
                .map(operation -> {
                    // Добавляем форматированные даты
                    operation.setFormattedStartedAt(
                            operation.getStartedAt() != null ?
                                    operation.getStartedAt().format(DATE_FORMATTER) : "N/A"
                    );

                    if (operation.getCompletedAt() != null) {
                        operation.setFormattedCompletedAt(
                                operation.getCompletedAt().format(DATE_FORMATTER)
                        );
                    }

                    // Добавляем CSS классы для статусов
                    operation.setStatusClass(getStatusClass(operation.getStatus()));
                    operation.setStatusDisplay(getStatusDisplay(operation.getStatus()));
                    operation.setOperationTypeDisplay(getOperationTypeDisplay(operation.getOperationType()));

                    model.addAttribute("operation", operation);
                    return "operations/status";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Операция с ID " + id + " не найдена");
                    return "redirect:/";
                });
    }

    /**
     * Список всех операций
     */
    @GetMapping
    public String listOperations(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 Model model) {
        log.debug("GET request to list all operations");

        Page<FileOperation> operations = operationRepository.findRecentOperations(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        );

        // Форматируем операции для отображения
        operations.forEach(op -> {
            op.setStatusClass(getStatusClass(op.getStatus()));
            op.setStatusDisplay(getStatusDisplay(op.getStatus()));
            op.setOperationTypeDisplay(getOperationTypeDisplay(op.getOperationType()));
            op.setFormattedStartedAt(
                    op.getStartedAt() != null ?
                            op.getStartedAt().format(DATE_FORMATTER) : "N/A"
            );
        });

        model.addAttribute("operations", operations);
        return "operations/list";
    }

    /**
     * Операции клиента
     */
    @GetMapping("/client/{clientId}")
    public String listClientOperations(@PathVariable Long clientId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        log.debug("GET request to list operations for client: {}", clientId);

        Page<FileOperation> operations = operationRepository.findByClientIdOrderByStartedAtDesc(
                clientId, PageRequest.of(page, size)
        );

        if (operations.isEmpty()) {
            model.addAttribute("infoMessage", "У этого клиента пока нет операций");
        }

        model.addAttribute("operations", operations);
        model.addAttribute("clientId", clientId);
        return "operations/client-list";
    }

    /**
     * Отмена операции (если возможно)
     */
    @PostMapping("/{id}/cancel")
    public String cancelOperation(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        log.debug("POST request to cancel operation: {}", id);

        operationRepository.findById(id).ifPresent(operation -> {
            if (operation.getStatus() == FileOperation.OperationStatus.PENDING ||
                    operation.getStatus() == FileOperation.OperationStatus.PROCESSING) {

                operation.markAsFailed("Операция отменена пользователем");
                operationRepository.save(operation);

                redirectAttributes.addFlashAttribute("successMessage",
                        "Операция отменена");
            } else {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Операция не может быть отменена в текущем статусе");
            }
        });

        return "redirect:/operations/" + id + "/status";
    }

    // Вспомогательные методы для форматирования

    private String getStatusClass(FileOperation.OperationStatus status) {
        switch (status) {
            case PENDING: return "status-pending";
            case PROCESSING: return "status-processing";
            case COMPLETED: return "status-success";
            case FAILED: return "status-error";
            default: return "status-unknown";
        }
    }

    private String getStatusDisplay(FileOperation.OperationStatus status) {
        switch (status) {
            case PENDING: return "Ожидание";
            case PROCESSING: return "В процессе";
            case COMPLETED: return "Завершено";
            case FAILED: return "Ошибка";
            default: return "Неизвестно";
        }
    }

    private String getOperationTypeDisplay(FileOperation.OperationType type) {
        switch (type) {
            case IMPORT: return "Импорт";
            case EXPORT: return "Экспорт";
            case PROCESS: return "Обработка";
            default: return "Неизвестно";
        }
    }
}