package by.zoomos.service;

import by.zoomos.model.entity.ProcessingStatus;
import by.zoomos.repository.ProcessingStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Сервис для работы со статусами обработки файлов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingStatusService {

    private final ProcessingStatusRepository statusRepository;
    private static final int STUCK_PROCESS_THRESHOLD_MINUTES = 30;

    /**
     * Создает новый статус обработки
     */
    @Transactional
    public ProcessingStatus createStatus(Long clientId, String fileName, Long fileSize) {
        log.debug("Создание нового статуса обработки для файла: {}", fileName);
        ProcessingStatus status = ProcessingStatus.createNew(clientId, fileName, fileSize);
        return statusRepository.save(status);
    }

    /**
     * Обновляет прогресс обработки
     */
    @Transactional
    public void updateProgress(Long statusId, int processedRecords, int totalRecords) {
        log.debug("Обновление прогресса обработки. StatusId: {}, Progress: {}/{}",
                statusId, processedRecords, totalRecords);

        ProcessingStatus status = statusRepository.findById(statusId)
                .orElseThrow(() -> new IllegalArgumentException("Статус не найден: " + statusId));

        status.setStatus(ProcessingStatus.Status.PROCESSING);
        status.updateProgress(processedRecords, totalRecords);
        statusRepository.save(status);
    }

    /**
     * Помечает обработку как завершенную
     */
    @Transactional
    public void markCompleted(Long statusId) {
        log.debug("Пометка обработки как завершенной. StatusId: {}", statusId);

        ProcessingStatus status = statusRepository.findById(statusId)
                .orElseThrow(() -> new IllegalArgumentException("Статус не найден: " + statusId));

        status.markCompleted();
        statusRepository.save(status);
    }

    /**
     * Помечает обработку как проваленную
     */
    @Transactional
    public void markFailed(Long statusId, String errorMessage) {
        log.error("Пометка обработки как проваленной. StatusId: {}, Error: {}", statusId, errorMessage);

        ProcessingStatus status = statusRepository.findById(statusId)
                .orElseThrow(() -> new IllegalArgumentException("Статус не найден: " + statusId));

        status.markFailed(errorMessage);
        statusRepository.save(status);
    }

    /**
     * Получает статус по ID
     */
    @Transactional(readOnly = true)
    public ProcessingStatus getStatus(Long statusId) {
        return statusRepository.findById(statusId)
                .orElseThrow(() -> new IllegalArgumentException("Статус не найден: " + statusId));
    }

    /**
     * Получает все статусы для клиента с пагинацией
     */
    @Transactional(readOnly = true)
    public Page<ProcessingStatus> getClientStatuses(Long clientId, Pageable pageable) {
        return statusRepository.findByClientIdOrderByCreatedAtDesc(clientId, pageable);
    }

    /**
     * Получает активные процессы для клиента
     */
    @Transactional(readOnly = true)
    public List<ProcessingStatus> getActiveProcesses(Long clientId) {
        return statusRepository.findActiveProcesses(clientId);
    }

    /**
     * Получает статистику обработки за период
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProcessingStatistics(Long clientId,
                                                       LocalDateTime startDate,
                                                       LocalDateTime endDate) {
        return statusRepository.getProcessingStatistics(clientId, startDate, endDate);
    }

    /**
     * Проверяет и обрабатывает зависшие процессы
     * Запускается каждый час
     */
    @Scheduled(fixedRate = 3600000) // 1 час
    @Transactional
    public void handleStuckProcesses() {
        log.debug("Запуск проверки зависших процессов");

        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(STUCK_PROCESS_THRESHOLD_MINUTES);

        List<ProcessingStatus> stuckProcesses = statusRepository.findStuckProcesses(threshold);

        for (ProcessingStatus status : stuckProcesses) {
            log.warn("Обнаружен зависший процесс: {}", status.getId());
            status.markFailed("Процесс был прерван из-за превышения времени ожидания");
            statusRepository.save(status);
        }

        if (!stuckProcesses.isEmpty()) {
            log.info("Обработано {} зависших процессов", stuckProcesses.size());
        }
    }
}
