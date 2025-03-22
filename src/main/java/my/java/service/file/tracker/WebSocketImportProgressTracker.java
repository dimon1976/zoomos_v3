package my.java.service.file.tracker;

import lombok.extern.slf4j.Slf4j;
import my.java.controller.ImportProgressController;
import my.java.service.file.importer.FileImportService;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Реализация трекера прогресса импорта с использованием WebSocket
 * для отправки обновлений прогресса в реальном времени
 */
@Service
@Slf4j
public class WebSocketImportProgressTracker implements ImportProgressTracker {

    private final ImportProgressController progressController;
    private final FileImportService fileImportService;

    // Кэш для хранения текущего состояния операций
    // operationId -> Map<String, Object>
    private final Map<Long, Map<String, Object>> operationsState = new ConcurrentHashMap<>();

    // Кэш для хранения счетчиков отправленных обновлений
    // operationId -> counter
    private final Map<Long, AtomicInteger> updateCounters = new ConcurrentHashMap<>();

    // Кэш для хранения счетчиков обработанных записей
    // operationId -> processedRecords
    private final Map<Long, AtomicInteger> processedRecordsCounters = new ConcurrentHashMap<>();

    // Интервал между обновлениями (в количестве записей)
    private static final int UPDATE_INTERVAL = 10; // Уменьшил интервал для более частых обновлений

    // Максимальное количество обновлений для одной операции
    private static final int MAX_UPDATES = 500;

    // Используем @Lazy для обеих зависимостей, чтобы разорвать циклическую зависимость
    public WebSocketImportProgressTracker(
            @Lazy ImportProgressController progressController,
            @Lazy FileImportService fileImportService) {
        this.progressController = progressController;
        this.fileImportService = fileImportService;
        log.info("WebSocketImportProgressTracker инициализирован");
    }

    @Override
    public void initProgress(Long operationId, int initialProgress) {
        log.info("Инициализация прогресса для операции #{}: ожидается {} записей", operationId, initialProgress);

        // Инициализируем состояние операции
        Map<String, Object> state = new ConcurrentHashMap<>();
        state.put("progress", 0);
        state.put("processedRecords", 0);
        state.put("totalRecords", initialProgress);
        state.put("completed", false);
        state.put("successful", false);

        operationsState.put(operationId, state);
        updateCounters.put(operationId, new AtomicInteger(0));
        processedRecordsCounters.put(operationId, new AtomicInteger(0));

        // Отправляем начальное обновление
        sendUpdate(operationId, state);
    }

    @Override
    @Async
    public void updateProgress(Long operationId, int processedRecords) {
        log.debug("Обновление прогресса для операции #{}: обработано {} записей", operationId, processedRecords);

        // Получаем текущее состояние операции
        Map<String, Object> state = operationsState.computeIfAbsent(operationId, k -> new ConcurrentHashMap<>());

        // Получаем счетчик обновлений
        AtomicInteger counter = updateCounters.computeIfAbsent(operationId, k -> new AtomicInteger(0));

        // Обновляем счетчик обработанных записей
        processedRecordsCounters.computeIfAbsent(operationId, k -> new AtomicInteger(0)).set(processedRecords);

        // Получаем актуальную информацию об операции
        try {
            // Обновляем счетчик только если прошло достаточно записей или это первое обновление
            boolean shouldUpdate = processedRecords % UPDATE_INTERVAL == 0 ||
                    counter.get() == 0 ||
                    processedRecords == 1;

            // Ограничиваем количество обновлений для предотвращения перегрузки
            if (shouldUpdate && counter.get() < MAX_UPDATES) {
                // Получаем актуальную информацию об операции из сервиса
                try {
                    var operation = fileImportService.getImportStatus(operationId);

                    // Вычисляем прогресс
                    int totalRecords = operation.getTotalRecords() != null ? operation.getTotalRecords() : 0;
                    int progress = totalRecords > 0 ? (int) ((long) processedRecords * 100 / totalRecords) : 0;

                    // Обновляем состояние
                    state.put("progress", progress);
                    state.put("processedRecords", processedRecords);
                    state.put("totalRecords", totalRecords);
                    state.put("status", operation.getStatus().toString());

                    // Отправляем обновление
                    sendUpdate(operationId, state);

                    // Увеличиваем счетчик обновлений
                    int updateCount = counter.incrementAndGet();

                    log.info("Отправлено обновление #{} для операции #{}: {}% ({} из {} записей)",
                            updateCount, operationId, progress, processedRecords, totalRecords);
                } catch (Exception e) {
                    // В случае ошибки запроса к сервису, используем данные из кэша
                    log.warn("Не удалось получить информацию об операции #{} из сервиса: {}", operationId, e.getMessage());

                    // Рассчитываем прогресс на основе кэшированных данных
                    int totalRecords = state.containsKey("totalRecords") ? (Integer)state.get("totalRecords") : 0;
                    int progress = totalRecords > 0 ? (int) ((long) processedRecords * 100 / totalRecords) : 0;

                    // Обновляем состояние
                    state.put("progress", progress);
                    state.put("processedRecords", processedRecords);

                    // Отправляем обновление
                    sendUpdate(operationId, state);

                    // Увеличиваем счетчик обновлений
                    counter.incrementAndGet();
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при обновлении прогресса операции #{}: {}", operationId, e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void incrementProgress(Long operationId, int increment) {
        // Получаем текущий счетчик обработанных записей
        AtomicInteger counter = processedRecordsCounters.computeIfAbsent(operationId, k -> new AtomicInteger(0));

        // Увеличиваем счетчик
        int newValue = counter.addAndGet(increment);

        log.debug("Увеличен счетчик прогресса для операции #{} на {}, новое значение: {}",
                operationId, increment, newValue);

        // Обновляем прогресс
        updateProgress(operationId, newValue);
    }

    @Override
    public void completeProgress(Long operationId, boolean success, int processedRecords, int totalRecords, String errorMessage) {
        log.info("Завершение прогресса для операции #{}: успешно: {}, обработано: {}, всего: {}",
                operationId, success, processedRecords, totalRecords);

        // Получаем текущее состояние операции
        Map<String, Object> state = operationsState.computeIfAbsent(operationId, k -> new ConcurrentHashMap<>());

        // Обновляем состояние
        state.put("completed", true);
        state.put("successful", success);
        state.put("processedRecords", processedRecords);
        state.put("totalRecords", totalRecords);
        state.put("progress", success ? 100 : 0);

        // Добавляем сообщение об ошибке, если есть
        if (!success && errorMessage != null) {
            state.put("errorMessage", errorMessage);
        }

        // Отправляем финальное обновление
        sendUpdate(operationId, state);

        // Записываем в лог
        if (success) {
            log.info("Операция #{} успешно завершена, обработано: {}, успешно: {}",
                    operationId, processedRecords, totalRecords);
        } else {
            log.error("Операция #{} завершена с ошибкой: {}", operationId, errorMessage);
        }

        // Отправляем WebSocket-уведомление о завершении
        log.debug("Отправлено WebSocket-уведомление о завершении: операция #{}, успешно: {}", operationId, success);

        // Очищаем кэш
        operationsState.remove(operationId);
        updateCounters.remove(operationId);
        processedRecordsCounters.remove(operationId);
    }

    /**
     * Отправляет обновление через WebSocket
     *
     * @param operationId ID операции
     * @param state текущее состояние операции
     */
    private void sendUpdate(Long operationId, Map<String, Object> state) {
        try {
            log.debug("Отправка WebSocket-обновления для операции #{}: {}", operationId, state);

            // Создаем копию состояния для отправки
            Map<String, Object> update = new HashMap<>(state);

            // Добавляем ID операции
            update.put("operationId", operationId);

            // Добавляем сообщение о прогрессе
            int processedRecords = (int) update.getOrDefault("processedRecords", 0);
            int totalRecords = (int) update.getOrDefault("totalRecords", 0);
            int progress = (int) update.getOrDefault("progress", 0);

            String message = String.format("Обработано %d из %d записей (%d%%)",
                    processedRecords, totalRecords, progress);
            update.put("message", message);

            // Отправляем обновление через контроллер
            progressController.sendProgressUpdate(operationId, update);
        } catch (Exception e) {
            log.error("Ошибка при отправке WebSocket-уведомления: {}", e.getMessage(), e);
        }
    }
}