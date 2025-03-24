package my.java.service.file.exporter.tracker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Трекер прогресса операций экспорта
 * Путь: /java/my/java/service/file/exporter/tracker/ExportProgressTracker.java
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportProgressTracker {

    private final SimpMessagingTemplate messagingTemplate;
    private final FileOperationRepository fileOperationRepository;

    // Кэш для хранения информации о прогрессе
    private final Map<Long, Map<String, Object>> progressCache = new ConcurrentHashMap<>();

    /**
     * Инициализирует операцию экспорта
     * @param operationId идентификатор операции
     * @param description описание операции
     */
    public void initializeOperation(Long operationId, String description) {
        log.debug("Инициализация операции экспорта #{}: {}", operationId, description);

        Map<String, Object> progressInfo = new HashMap<>();
        progressInfo.put("operationId", operationId);
        progressInfo.put("description", description);
        progressInfo.put("status", "STARTED");
        progressInfo.put("total", 0);
        progressInfo.put("processed", 0);
        progressInfo.put("progress", 0);

        progressCache.put(operationId, progressInfo);

        // Отправляем начальное уведомление
        sendProgressUpdate(operationId, progressInfo);
    }

    /**
     * Обновляет общее количество записей для экспорта
     * @param operationId идентификатор операции
     * @param total общее количество записей
     */
    public void updateTotal(Long operationId, int total) {
        log.debug("Обновление общего количества записей для операции #{}: {}", operationId, total);

        Map<String, Object> progressInfo = progressCache.computeIfAbsent(operationId, id -> new HashMap<>());
        progressInfo.put("total", total);

        // Обновляем прогресс, если уже есть обработанные записи
        if (progressInfo.containsKey("processed")) {
            int processed = (int) progressInfo.getOrDefault("processed", 0);
            if (total > 0 && processed > 0) {
                int progress = Math.min(100, (int)((processed * 100.0) / total));
                progressInfo.put("progress", progress);
            }
        }

        // Обновляем в БД
        updateOperationInDb(operationId, total, -1);

        // Отправляем уведомление
        sendProgressUpdate(operationId, progressInfo);
    }

    /**
     * Обновляет количество обработанных записей
     * @param operationId идентификатор операции
     * @param processed количество обработанных записей
     */
    public void updateProgress(Long operationId, int processed) {
        log.debug("Обновление прогресса для операции #{}: {} записей", operationId, processed);

        Map<String, Object> progressInfo = progressCache.computeIfAbsent(operationId, id -> new HashMap<>());
        progressInfo.put("processed", processed);

        // Обновляем процент выполнения
        int total = (int) progressInfo.getOrDefault("total", 0);
        if (total > 0) {
            int progress = Math.min(100, (int)((processed * 100.0) / total));
            progressInfo.put("progress", progress);
        }

        // Обновляем в БД
        updateOperationInDb(operationId, -1, processed);

        // Отправляем уведомление
        sendProgressUpdate(operationId, progressInfo);
    }

    /**
     * Обновляет статус операции
     * @param operationId идентификатор операции
     * @param status текстовый статус операции
     */
    public void updateStatus(Long operationId, String status) {
        log.debug("Обновление статуса для операции #{}: {}", operationId, status);

        Map<String, Object> progressInfo = progressCache.computeIfAbsent(operationId, id -> new HashMap<>());
        progressInfo.put("status", status);

        // Отправляем уведомление
        sendProgressUpdate(operationId, progressInfo);
    }

    /**
     * Помечает операцию как завершенную
     * @param operationId идентификатор операции
     * @param message сообщение о завершении
     */
    public void complete(Long operationId, String message) {
        log.debug("Завершение операции экспорта #{}: {}", operationId, message);

        Map<String, Object> progressInfo = progressCache.computeIfAbsent(operationId, id -> new HashMap<>());
        progressInfo.put("status", "COMPLETED");
        progressInfo.put("message", message);

        // Устанавливаем 100% прогресс
        int total = (int) progressInfo.getOrDefault("total", 0);
        if (total > 0) {
            progressInfo.put("processed", total);
            progressInfo.put("progress", 100);
        }

        // Обновляем операцию в БД
        fileOperationRepository.findById(operationId).ifPresent(operation -> {
            operation.markAsCompleted(total);
            fileOperationRepository.save(operation);
        });

        // Отправляем уведомление
        sendProgressUpdate(operationId, progressInfo);

        // Очищаем из кэша через некоторое время
        scheduleClearProgress(operationId);
    }

    /**
     * Помечает операцию как завершенную с ошибкой
     * @param operationId идентификатор операции
     * @param errorMessage сообщение об ошибке
     */
    public void error(Long operationId, String errorMessage) {
        log.debug("Ошибка операции экспорта #{}: {}", operationId, errorMessage);

        Map<String, Object> progressInfo = progressCache.computeIfAbsent(operationId, id -> new HashMap<>());
        progressInfo.put("status", "ERROR");
        progressInfo.put("message", errorMessage);

        // Обновляем операцию в БД
        fileOperationRepository.findById(operationId).ifPresent(operation -> {
            operation.markAsFailed(errorMessage);
            fileOperationRepository.save(operation);
        });

        // Отправляем уведомление
        sendProgressUpdate(operationId, progressInfo);

        // Очищаем из кэша через некоторое время
        scheduleClearProgress(operationId);
    }

    /**
     * Получает информацию о прогрессе операции
     * @param operationId идентификатор операции
     * @return информация о прогрессе
     */
    public Map<String, Object> getProgressInfo(Long operationId) {
        return progressCache.getOrDefault(operationId, new HashMap<>());
    }

    /**
     * Отправляет обновление прогресса через WebSocket
     * @param operationId идентификатор операции
     * @param progressInfo информация о прогрессе
     */
    private void sendProgressUpdate(Long operationId, Map<String, Object> progressInfo) {
        try {
            // Отправляем уведомление о прогрессе экспорта
            messagingTemplate.convertAndSend("/topic/export-progress/" + operationId, progressInfo);

            // Также отправляем в общий канал
            messagingTemplate.convertAndSend("/topic/export-progress", progressInfo);
        } catch (Exception e) {
            log.error("Ошибка при отправке обновления прогресса: {}", e.getMessage(), e);
        }
    }

    /**
     * Обновляет информацию о прогрессе операции в БД
     * @param operationId идентификатор операции
     * @param total общее количество записей (-1 если не требуется обновление)
     * @param processed обработанное количество записей (-1 если не требуется обновление)
     */
    private void updateOperationInDb(Long operationId, int total, int processed) {
        fileOperationRepository.findById(operationId).ifPresent(operation -> {
            if (total >= 0) {
                operation.setTotalRecords(total);
            }

            if (processed >= 0) {
                operation.setProcessedRecords(processed);

                // Обновляем процент выполнения
                if (operation.getTotalRecords() != null && operation.getTotalRecords() > 0) {
                    int progress = Math.min(100, (int)((processed * 100.0) / operation.getTotalRecords()));
                    operation.setProcessingProgress(progress);
                }
            }

            // Если операция еще не в процессе, обновляем статус
            if (operation.getStatus() == FileOperation.OperationStatus.PENDING) {
                operation.markAsProcessing();
            }

            fileOperationRepository.save(operation);
        });
    }

    /**
     * Планирует очистку информации о прогрессе из кэша
     * @param operationId идентификатор операции
     */
    private void scheduleClearProgress(Long operationId) {
        // Запускаем новый поток, который удалит запись через 5 минут
        new Thread(() -> {
            try {
                // Ждем 5 минут
                Thread.sleep(5 * 60 * 1000);
                // Удаляем информацию о прогрессе из кэша
                progressCache.remove(operationId);
                log.debug("Информация о прогрессе для операции #{} удалена из кэша", operationId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}