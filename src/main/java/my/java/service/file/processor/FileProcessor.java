package my.java.service.file.processor;

import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс для процессоров файлов различных форматов.
 * Определяет методы для чтения, анализа и обработки файлов.
 */
public interface FileProcessor {

    /**
     * Получает типы файлов, поддерживаемые этим процессором.
     *
     * @return массив расширений файлов (например, "csv", "xlsx")
     */
    String[] getSupportedFileExtensions();

    /**
     * Получает MIME-типы, поддерживаемые этим процессором.
     *
     * @return массив MIME-типов (например, "text/csv", "application/vnd.ms-excel")
     */
    String[] getSupportedMimeTypes();

    /**
     * Проверяет, подходит ли данный процессор для обработки указанного файла.
     *
     * @param filePath путь к файлу
     * @return true, если процессор может обработать файл
     */
    boolean canProcess(Path filePath);

    /**
     * Обрабатывает файл и создает список сущностей из его данных.
     *
     * @param filePath путь к файлу
     * @param entityType тип создаваемых сущностей
     * @param client клиент, для которого производится импорт
     * @param fieldMapping маппинг полей файла к полям сущности
     * @param params дополнительные параметры обработки
     * @param operation объект операции для отслеживания прогресса
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
     * @param params дополнительные параметры анализа
     * @return карта с информацией о структуре файла (заголовки, типы данных, примеры и т.д.)
     */
    Map<String, Object> analyzeFile(Path filePath, Map<String, String> params);

    /**
     * Проверяет базовую валидность файла.
     *
     * @param filePath путь к файлу
     * @return результат проверки (true - файл валиден)
     */
    boolean validateFile(Path filePath);

    /**
     * Оценивает количество записей в файле без полной его обработки.
     *
     * @param filePath путь к файлу
     * @return примерное количество записей или -1, если оценка невозможна
     */
    int estimateRecordCount(Path filePath);

    /**
     * Определяет, поддерживает ли процессор обработку потоком (для больших файлов).
     *
     * @return true, если поддерживается потоковая обработка
     */
    boolean supportsStreaming();

    /**
     * Получает дополнительные параметры конфигурации, которые может принимать процессор.
     *
     * @return карта с описанием параметров
     */
    Map<String, Object> getConfigParameters();
}