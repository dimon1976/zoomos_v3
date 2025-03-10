package my.java.service.file.tracker;

import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Компонент для отслеживания прогресса операций импорта файлов.
 * Предоставляет методы для обновления и получения информации о прогрессе.
 */
@Component
@Slf4j
public class ImportProgressTracker {

    private final FileOperationRepository fileOperationRepository;

    // Карта с информацией о прогрессе для каждой операции (id операции -> информация о прогрессе)
    private final Map<Long, ProgressInfo> progressMap = new ConcurrentHashMap<>();

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param fileOperationRepository репозиторий операций с файлами
     */
    public ImportProgressTracker(FileOperationRepository fileOperationRepository) {
        this.fileOperationRepository = fileOperationRepository;
    }

    /**
     * Инициализирует отслеживание прогресса для новой операции.
     *
     * @param operationId ID операции
     * @param totalRecords общее количество записей для обработки
     * @return информация о прогрессе
     */
    public ProgressInfo initProgress(Long operationId, int totalRecords) {
        log.debug("Инициализация отслеживания прогресса для операции #{}, всего записей: {}", operationId, totalRecords);

        ProgressInfo progressInfo = new ProgressInfo(totalRecords);
        progressMap.put(operationId, progressInfo);

        // Обновляем информацию в БД
        updateOperationInDb(operationId, 0, totalRecords, 0);

        return progressInfo;
    }

    /**
     * Обновляет прогресс операции.
     *
     * @param operationId ID операции
     * @param processedRecords количество обработанных записей
     * @return текущий прогресс в процентах (0-100)
     */
    public int updateProgress(Long operationId, int processedRecords) {
        ProgressInfo progressInfo = progressMap.get(operationId);

        if (progressInfo == null) {
            log.warn("Попытка обновить прогресс для неотслеживаемой операции #{}", operationId);
            return 0;
        }

        // Обновляем количество обработанных записей
        progressInfo.setProcessedRecords(processedRecords);

        // Вычисляем процент выполнения
        int progressPercent = calculateProgressPercent(progressInfo);

        // Если процент изменился, обновляем информацию в БД
        if (progressPercent != progressInfo.getLastReportedPercent()) {
            progressInfo.setLastReportedPercent(progressPercent);
            updateOperationInDb(operationId, processedRecords, progressInfo.getTotalRecords(), progressPercent);
        }

        return progressPercent;
    }

    /**
     * Увеличивает счетчик обработанных записей на указанное значение.
     *
     * @param operationId ID операции
     * @param incrementBy значение, на которое нужно увеличить счетчик
     * @return текущий прогресс в процентах (0-100)
     */
    public int incrementProgress(Long operationId, int incrementBy) {
        ProgressInfo progressInfo = progressMap.get(operationId);

        if (progressInfo == null) {
            log.warn("Попытка обновить прогресс для неотслеживаемой операции #{}", operationId);
            return 0;
        }

        // Увеличиваем счетчик обработанных записей
        int newProcessedRecords = progressInfo.getProcessedRecords().addAndGet(incrementBy);

        // Вычисляем процент выполнения
        int progressPercent = calculateProgressPercent(progressInfo);

        // Если процент изменился или прошло достаточно времени с последнего обновления, обновляем информацию в БД
        if (progressPercent != progressInfo.getLastReportedPercent() ||
                shouldUpdateDatabase(progressInfo)) {
            progressInfo.setLastReportedPercent(progressPercent);
            progressInfo.setLastDbUpdateTime(System.currentTimeMillis());
            updateOperationInDb(operationId, newProcessedRecords, progressInfo.getTotalRecords(), progressPercent);
        }

        return progressPercent;
    }

    /**
     * Получает текущую информацию о прогрессе.
     *
     * @param operationId ID операции
     * @return информация о прогрессе или null, если операция не отслеживается
     */
    public ProgressInfo getProgressInfo(Long operationId) {
        return progressMap.get(operationId);
    }

    /**
     * Получает текущий прогресс в процентах.
     *
     * @param operationId ID операции
     * @return прогресс в процентах (0-100) или -1, если операция не отслеживается
     */
    public int getProgressPercent(Long operationId) {
        ProgressInfo progressInfo = progressMap.get(operationId);

        if (progressInfo == null) {
            return -1;
        }

        return calculateProgressPercent(progressInfo);
    }

    /**
     * Завершает отслеживание прогресса для операции.
     *
     * @param operationId ID операции
     * @param successful true, если операция завершилась успешно
     * @param totalProcessed общее количество обработанных записей
     * @param successfulRecords количество успешно обработанных записей
     * @param errorMessage сообщение об ошибке (если операция не успешна)
     */
    public void completeProgress(Long operationId, boolean successful, int totalProcessed,
                                 int successfulRecords, String errorMessage) {
        ProgressInfo progressInfo = progressMap.get(operationId);

        if (progressInfo == null) {
            log.warn("Попытка завершить прогресс для неотслеживаемой операции #{}", operationId);
            return;
        }

        // Обновляем информацию о завершении в БД
        try {
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция с ID " + operationId + " не найдена"));

            if (successful) {
                operation.markAsCompleted(successfulRecords);
            } else {
                operation.markAsFailed(errorMessage);
            }

            // Устанавливаем окончательное значение прогресса
            operation.setProcessedRecords(totalProcessed);
            operation.setProcessingProgress(100);
            operation.setTotalRecords(progressInfo.getTotalRecords());

            fileOperationRepository.save(operation);

            log.info("Операция #{} {} обработано: {}, успешно: {}",
                    operationId,
                    successful ? "успешно завершена," : "завершена с ошибкой,",
                    totalProcessed,
                    successfulRecords);
        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса операции #{}: {}", operationId, e.getMessage());
        } finally {
            // Удаляем информацию о прогрессе из карты
            progressMap.remove(operationId);
        }
    }

    /**
     * Вычисляет процент выполнения операции.
     *
     * @param progressInfo информация о прогрессе
     * @return процент выполнения (0-100)
     */
    private int calculateProgressPercent(ProgressInfo progressInfo) {
        int totalRecords = progressInfo.getTotalRecords();
        int processedRecords = progressInfo.getProcessedRecords().get();

        if (totalRecords <= 0) {
            return 0;
        }

        return Math.min(100, (int) ((processedRecords * 100.0) / totalRecords));
    }

    /**
     * Определяет, нужно ли обновлять информацию в БД.
     *
     * @param progressInfo информация о прогрессе
     * @return true, если нужно обновить информацию в БД
     */
    private boolean shouldUpdateDatabase(ProgressInfo progressInfo) {
        // Обновляем БД не чаще чем раз в секунду
        long timeSinceLastUpdate = System.currentTimeMillis() - progressInfo.getLastDbUpdateTime();
        return timeSinceLastUpdate > 1000;
    }

    /**
     * Обновляет информацию об операции в БД.
     *
     * @param operationId ID операции
     * @param processedRecords количество обработанных записей
     * @param totalRecords общее количество записей
     * @param progressPercent процент выполнения
     */
    private void updateOperationInDb(Long operationId, int processedRecords, int totalRecords, int progressPercent) {
        try {
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция с ID " + operationId + " не найдена"));

            operation.setProcessedRecords(processedRecords);
            operation.setTotalRecords(totalRecords);
            operation.setProcessingProgress(progressPercent);

            fileOperationRepository.save(operation);

            log.debug("Обновлен прогресс для операции #{}: {}% ({} из {})",
                    operationId, progressPercent, processedRecords, totalRecords);
        } catch (Exception e) {
            log.error("Ошибка при обновлении прогресса операции #{}: {}", operationId, e.getMessage());
        }
    }

    /**
     * Класс для хранения информации о прогрессе операции.
     */
    public static class ProgressInfo {
        // Общее количество записей для обработки
        private final int totalRecords;

        // Количество обработанных записей (атомарное для потокобезопасности)
        private final AtomicInteger processedRecords = new AtomicInteger(0);

        // Последний отправленный процент выполнения (для оптимизации обновлений)
        private int lastReportedPercent = 0;

        // Время последнего обновления в БД (для оптимизации обновлений)
        private long lastDbUpdateTime = System.currentTimeMillis();

        // Время начала операции
        private final long startTime = System.currentTimeMillis();

        /**
         * Конструктор с указанием общего количества записей.
         *
         * @param totalRecords общее количество записей
         */
        public ProgressInfo(int totalRecords) {
            this.totalRecords = totalRecords;
        }

        /**
         * Получает общее количество записей.
         *
         * @return общее количество записей
         */
        public int getTotalRecords() {
            return totalRecords;
        }

        /**
         * Получает атомарный счетчик обработанных записей.
         *
         * @return счетчик обработанных записей
         */
        public AtomicInteger getProcessedRecords() {
            return processedRecords;
        }

        /**
         * Устанавливает количество обработанных записей.
         *
         * @param processedRecords количество обработанных записей
         */
        public void setProcessedRecords(int processedRecords) {
            this.processedRecords.set(processedRecords);
        }

        /**
         * Получает последний отправленный процент выполнения.
         *
         * @return процент выполнения
         */
        public int getLastReportedPercent() {
            return lastReportedPercent;
        }

        /**
         * Устанавливает последний отправленный процент выполнения.
         *
         * @param lastReportedPercent процент выполнения
         */
        public void setLastReportedPercent(int lastReportedPercent) {
            this.lastReportedPercent = lastReportedPercent;
        }

        /**
         * Получает время последнего обновления в БД.
         *
         * @return время последнего обновления в миллисекундах
         */
        public long getLastDbUpdateTime() {
            return lastDbUpdateTime;
        }

        /**
         * Устанавливает время последнего обновления в БД.
         *
         * @param lastDbUpdateTime время в миллисекундах
         */
        public void setLastDbUpdateTime(long lastDbUpdateTime) {
            this.lastDbUpdateTime = lastDbUpdateTime;
        }

        /**
         * Получает время начала операции.
         *
         * @return время начала в миллисекундах
         */
        public long getStartTime() {
            return startTime;
        }

        /**
         * Рассчитывает приблизительное оставшееся время в миллисекундах.
         *
         * @return оставшееся время в миллисекундах или -1, если невозможно рассчитать
         */
        public long getEstimatedTimeRemaining() {
            int processed = processedRecords.get();
            if (processed <= 0 || totalRecords <= 0) {
                return -1;
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            double recordsPerMs = processed / (double) elapsedTime;
            long remainingRecords = totalRecords - processed;

            return (long) (remainingRecords / recordsPerMs);
        }
    }
}