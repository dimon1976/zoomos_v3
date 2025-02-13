package by.zoomos.controller;

import by.zoomos.model.entity.ProcessingStatus;
import by.zoomos.service.ProcessingStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы со статусами обработки файлов
 */
@RestController
@RequestMapping("/api/v1/processing-status")
@RequiredArgsConstructor
@Slf4j
public class ProcessingStatusController {

    private final ProcessingStatusService statusService;

    /**
     * Получает статус обработки по ID
     */
    @GetMapping("/{statusId}")
    public ResponseEntity<ProcessingStatus> getStatus(@PathVariable Long statusId) {
        log.debug("Получение статуса обработки: {}", statusId);
        return ResponseEntity.ok(statusService.getStatus(statusId));
    }

    /**
     * Получает все статусы клиента с пагинацией
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<Page<ProcessingStatus>> getClientStatuses(
            @PathVariable Long clientId,
            Pageable pageable) {
        log.debug("Получение статусов клиента: {}", clientId);
        return ResponseEntity.ok(statusService.getClientStatuses(clientId, pageable));
    }

    /**
     * Получает активные процессы клиента
     */
    @GetMapping("/client/{clientId}/active")
    public ResponseEntity<List<ProcessingStatus>> getActiveProcesses(
            @PathVariable Long clientId) {
        log.debug("Получение активных процессов клиента: {}", clientId);
        return ResponseEntity.ok(statusService.getActiveProcesses(clientId));
    }

    /**
     * Получает статистику обработки за период
     */
    @GetMapping("/client/{clientId}/statistics")
    public ResponseEntity<Map<String, Object>> getProcessingStatistics(
            @PathVariable Long clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.debug("Получение статистики обработки. ClientId: {}, Period: {} - {}",
                clientId, startDate, endDate);
        return ResponseEntity.ok(statusService.getProcessingStatistics(clientId, startDate, endDate));
    }

    /**
     * Отменяет обработку файла
     */
    @PostMapping("/{statusId}/cancel")
    public ResponseEntity<Void> cancelProcessing(
            @PathVariable Long statusId,
            @RequestParam(required = false) String reason) {
        log.info("Отмена обработки файла. StatusId: {}, Reason: {}", statusId, reason);

        ProcessingStatus status = statusService.getStatus(statusId);
        status.markCancelled(reason);

        return ResponseEntity.ok().build();
    }
}