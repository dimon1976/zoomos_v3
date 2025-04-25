package my.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.model.FileOperation;
import my.java.model.FileOperation.OperationStatus;
import my.java.model.FileOperation.OperationType;
import my.java.service.client.ClientService;
import my.java.repository.FileOperationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер для просмотра и управления файловыми операциями
 */
@Controller
@RequestMapping("/clients/{clientId}/operations")
@RequiredArgsConstructor
@Slf4j
public class OperationController {

    private final ClientService clientService;
    private final FileOperationRepository fileOperationRepository;

    /**
     * Отображение списка операций для клиента
     */
    @GetMapping
    public String listOperations(
            @PathVariable Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        log.debug("GET запрос на получение списка операций для клиента ID: {}", clientId);

        // Получаем информацию о клиенте
        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("currentUri", request.getRequestURI());

                    // Добавляем параметры фильтра в модель для отображения в форме
                    model.addAttribute("operationType", operationType);
                    model.addAttribute("status", status);
                    model.addAttribute("dateFrom", dateFrom);
                    model.addAttribute("dateTo", dateTo);

                    // Создаем пагинацию для операций
                    Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
                    Page<FileOperation> operationsPage;

                    try {
                        // Применяем фильтры, если они указаны
                        operationsPage = getFilteredOperations(clientId, operationType, status, dateFrom, dateTo, pageable);

                        // Преобразуем в DTO для отображения
                        List<FileOperationDto> operations = operationsPage.getContent().stream()
                                .map(this::mapToDto)
                                .collect(Collectors.toList());

                        model.addAttribute("operations", operations);
                        model.addAttribute("currentPage", operationsPage.getNumber());
                        model.addAttribute("totalPages", operationsPage.getTotalPages());
                        model.addAttribute("totalItems", operationsPage.getTotalElements());

                    } catch (Exception e) {
                        log.error("Ошибка при получении операций: {}", e.getMessage(), e);
                        model.addAttribute("operations", new ArrayList<>());
                        model.addAttribute("errorMessage", "Ошибка при получении операций: " + e.getMessage());
                    }

                    return "operations/list";
                })
                .orElseGet(() -> {
                    log.warn("Клиент с ID {} не найден", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Получение отфильтрованных операций
     */
    private Page<FileOperation> getFilteredOperations(
            Long clientId, String operationType, String status,
            String dateFrom, String dateTo, Pageable pageable) {

        // Создаем спецификацию для фильтрации
        Specification<FileOperation> spec = Specification.where((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.join("client").get("id"), clientId));

        // Добавляем фильтр по типу операции
        if (operationType != null && !operationType.isEmpty()) {
            try {
                OperationType type = OperationType.valueOf(operationType);
                spec = spec.and((root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("operationType"), type));
            } catch (IllegalArgumentException e) {
                log.warn("Некорректный тип операции: {}", operationType);
            }
        }

        // Добавляем фильтр по статусу
        if (status != null && !status.isEmpty()) {
            try {
                OperationStatus statusEnum = OperationStatus.valueOf(status);
                spec = spec.and((root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("status"), statusEnum));
            } catch (IllegalArgumentException e) {
                log.warn("Некорректный статус: {}", status);
            }
        }

        // Добавляем фильтр по дате начала
        if (dateFrom != null && !dateFrom.isEmpty()) {
            try {
                ZonedDateTime startDate = LocalDate.parse(dateFrom)
                        .atStartOfDay(ZoneId.systemDefault());
                spec = spec.and((root, query, criteriaBuilder) ->
                        criteriaBuilder.greaterThanOrEqualTo(root.get("startedAt"), startDate));
            } catch (Exception e) {
                log.warn("Некорректная дата начала: {}", dateFrom);
            }
        }

        // Добавляем фильтр по дате окончания
        if (dateTo != null && !dateTo.isEmpty()) {
            try {
                ZonedDateTime endDate = LocalDate.parse(dateTo)
                        .plusDays(1) // Включаем весь день
                        .atStartOfDay(ZoneId.systemDefault());
                spec = spec.and((root, query, criteriaBuilder) ->
                        criteriaBuilder.lessThan(root.get("startedAt"), endDate));
            } catch (Exception e) {
                log.warn("Некорректная дата окончания: {}", dateTo);
            }
        }

        // Выполняем запрос с созданной спецификацией
        return fileOperationRepository.findAll(spec, pageable);
    }

    /**
     * Отображение детальной информации об операции
     */
    @GetMapping("/{operationId}")
    public String getOperationDetails(
            @PathVariable Long clientId,
            @PathVariable Long operationId,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.debug("GET запрос на получение деталей операции: {}", operationId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);

                    return fileOperationRepository.findById(operationId)
                            .map(operation -> {
                                if (!operation.getClient().getId().equals(clientId)) {
                                    redirectAttributes.addFlashAttribute("errorMessage",
                                            "Операция не принадлежит данному клиенту");
                                    return "redirect:/clients/" + clientId + "/operations";
                                }

                                model.addAttribute("operation", mapToDto(operation));
                                return "operations/details";
                            })
                            .orElseGet(() -> {
                                redirectAttributes.addFlashAttribute("errorMessage",
                                        "Операция с ID " + operationId + " не найдена");
                                return "redirect:/clients/" + clientId + "/operations";
                            });
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Преобразование сущности FileOperation в DTO
     */
    private FileOperationDto mapToDto(FileOperation operation) {
        if (operation == null) {
            return null;
        }

        return FileOperationDto.builder()
                .id(operation.getId())
                .clientId(operation.getClient() != null ? operation.getClient().getId() : null)
                .operationType(operation.getOperationType())
                .fileName(operation.getFileName())
                .fileType(operation.getFileType())
                .recordCount(operation.getRecordCount())
                .status(operation.getStatus())
                .errorMessage(operation.getErrorMessage())
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .build();
    }
}