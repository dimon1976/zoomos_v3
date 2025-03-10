package my.java.service.file.strategy;

import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс для стратегий обработки файлов.
 * Определяет способ чтения, валидации и преобразования данных из файла в сущности.
 */
public interface FileProcessingStrategy {

    /**
     * Уникальный идентификатор стратегии.
     *
     * @return идентификатор стратегии
     */
    String getStrategyId();

    /**
     * Название стратегии для отображения.
     *
     * @return название стратегии
     */
    String getDisplayName();

    /**
     * Описание стратегии.
     *
     * @return описание стратегии
     */
    String getDescription();

    /**
     * Поддерживаемые типы файлов.
     *
     * @return список поддерживаемых MIME-типов или расширений
     */
    List<String> getSupportedFileTypes();

    /**
     * Обрабатывает файл и возвращает список созданных сущностей.
     *
     * @param filePath путь к файлу
     * @param entityType тип сущности для создания
     * @param client клиент, для которого выполняется обработка
     * @param fieldMapping маппинг полей (ключ - заголовок в файле, значение - имя поля в сущности)
     * @param params дополнительные параметры обработки
     * @param operation объект операции для обновления прогресса
     * @return список созданных сущностей
     */
    List<ImportableEntity> processFile(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            Map<String, String> params,
            FileOperation operation);

    /**
     * Анализирует файл и возвращает информацию о его структуре.
     *
     * @param filePath путь к файлу
     * @return информация о структуре файла (заголовки, примеры данных и т.д.)
     */
    Map<String, Object> analyzeFile(Path filePath);

    /**
     * Проверяет, совместима ли стратегия с указанным файлом.
     *
     * @param filePath путь к файлу
     * @return true, если стратегия подходит для обработки этого файла
     */
    boolean isCompatibleWithFile(Path filePath);

    /**
     * Возвращает параметры стратегии, которые можно настроить.
     *
     * @return описание параметров в формате Map
     */
    Map<String, Object> getConfigurableParameters();

    /**
     * Выполняет валидацию параметров перед обработкой.
     *
     * @param params параметры для валидации
     * @return true, если параметры валидны
     */
    boolean validateParameters(Map<String, String> params);

    /**
     * Возвращает примерный объем памяти, необходимый для обработки файла.
     * Используется для принятия решения о режиме обработки (в памяти/потоковый).
     *
     * @param filePath путь к файлу
     * @return оценка необходимой памяти в байтах или -1, если невозможно оценить
     */
    long estimateMemoryRequirements(Path filePath);

    /**
     * Поддерживает ли стратегия потоковую обработку файла
     * (для больших файлов, которые не помещаются в память).
     *
     * @return true, если поддерживается потоковая обработка
     */
    boolean supportsStreaming();

    /**
     * Определяет приоритет стратегии при автоматическом выборе.
     * Стратегии с большим приоритетом выбираются первыми.
     *
     * @return приоритет стратегии (по умолчанию 0)
     */
    default int getPriority() {
        return 0;
    }
}