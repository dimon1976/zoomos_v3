package my.java.service.file.tracker;

/**
 * Интерфейс для отслеживания прогресса импорта файлов
 */
public interface ImportProgressTracker {

    /**
     * Инициализирует прогресс для новой операции
     *
     * @param operationId ID операции
     * @param initialProgress начальный прогресс (обычно 0)
     */
    void initProgress(Long operationId, int initialProgress);

    /**
     * Обновляет прогресс операции
     *
     * @param operationId ID операции
     * @param processedRecords количество обработанных записей
     */
    void updateProgress(Long operationId, int processedRecords);

    /**
     * Увеличивает счетчик обработанных записей на указанное значение
     *
     * @param operationId ID операции
     * @param increment величина, на которую нужно увеличить счетчик
     */
    void incrementProgress(Long operationId, int increment);

    /**
     * Завершает прогресс операции
     *
     * @param operationId ID операции
     * @param success флаг успешного завершения
     * @param processedRecords количество обработанных записей
     * @param totalRecords общее количество записей
     * @param errorMessage сообщение об ошибке (если есть)
     */
    void completeProgress(Long operationId, boolean success, int processedRecords, int totalRecords, String errorMessage);
}