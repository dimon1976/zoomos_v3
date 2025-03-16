// src/main/java/my/java/service/file/tracker/ProgressInfo.java
package my.java.service.file.tracker;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Класс для хранения информации о прогрессе операции импорта.
 */
public class ProgressInfo {
    private final int totalRecords;
    private final AtomicInteger processedRecords = new AtomicInteger(0);
    private int lastReportedPercent = 0;
    private long lastDbUpdateTime = System.currentTimeMillis();
    private final long startTime = System.currentTimeMillis();

    /**
     * Создает новый объект информации о прогрессе
     *
     * @param totalRecords общее количество записей для обработки
     */
    public ProgressInfo(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    /**
     * Возвращает общее количество записей для обработки
     *
     * @return общее количество записей
     */
    public int getTotalRecords() {
        return totalRecords;
    }

    /**
     * Возвращает атомарный счетчик обработанных записей
     *
     * @return счетчик обработанных записей
     */
    public AtomicInteger getProcessedRecords() {
        return processedRecords;
    }

    /**
     * Устанавливает количество обработанных записей
     *
     * @param processedRecords новое количество обработанных записей
     */
    public void setProcessedRecords(int processedRecords) {
        this.processedRecords.set(processedRecords);
    }

    /**
     * Возвращает последний отправленный процент прогресса
     *
     * @return процент прогресса
     */
    public int getLastReportedPercent() {
        return lastReportedPercent;
    }

    /**
     * Устанавливает последний отправленный процент прогресса
     *
     * @param lastReportedPercent новый процент прогресса
     */
    public void setLastReportedPercent(int lastReportedPercent) {
        this.lastReportedPercent = lastReportedPercent;
    }

    /**
     * Возвращает время последнего обновления базы данных
     *
     * @return время последнего обновления в миллисекундах
     */
    public long getLastDbUpdateTime() {
        return lastDbUpdateTime;
    }

    /**
     * Устанавливает время последнего обновления базы данных
     *
     * @param lastDbUpdateTime новое время обновления в миллисекундах
     */
    public void setLastDbUpdateTime(long lastDbUpdateTime) {
        this.lastDbUpdateTime = lastDbUpdateTime;
    }

    /**
     * Возвращает время начала операции
     *
     * @return время начала в миллисекундах
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Оценивает оставшееся время для завершения операции
     *
     * @return оставшееся время в миллисекундах или -1, если оценка невозможна
     */
    public long getEstimatedTimeRemaining() {
        int processed = processedRecords.get();

        if (processed <= 0 || totalRecords <= 0) {
            return -1;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        double recordsPerMs = processed / (double) elapsedTime;

        if (recordsPerMs <= 0) {
            return -1;
        }

        long remainingRecords = totalRecords - processed;
        return (long) (remainingRecords / recordsPerMs);
    }

    /**
     * Рассчитывает текущий процент выполнения
     *
     * @return текущий процент выполнения (0-100)
     */
    public int calculateProgressPercent() {
        if (totalRecords <= 0) {
            return 0;
        }

        int currentProcessed = processedRecords.get();
        return Math.min(100, (int) ((currentProcessed * 100.0) / totalRecords));
    }
}