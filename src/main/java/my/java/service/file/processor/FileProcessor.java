package my.java.service.file.processor;

import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.options.FileReadingOptions;

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
     * Анализирует файл с использованием объекта параметров FileReadingOptions.
     *
     * @param filePath путь к файлу
     * @param options параметры анализа
     * @return карта с информацией о структуре файла (заголовки, типы данных, примеры и т.д.)
     */
    default Map<String, Object> analyzeFileWithOptions(Path filePath, FileReadingOptions options) {
        // По умолчанию преобразуем в Map для обратной совместимости
        return analyzeFile(filePath, options != null ? options.toMap() : null);
    }

    /**
     * Обрабатывает файл и создает список сущностей из его данных с использованием FileReadingOptions.
     *
     * @param filePath путь к файлу
     * @param entityType тип создаваемых сущностей
     * @param client клиент, для которого производится импорт
     * @param fieldMapping маппинг полей файла к полям сущности
     * @param options параметры обработки в виде объекта FileReadingOptions
     * @param operation объект операции для отслеживания прогресса
     * @return список созданных сущностей
     */
    List<ImportableEntity> processFileWithOptions(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            FileReadingOptions options,
            FileOperation operation);

    /**
     * Читает сырые данные из файла с использованием FileReadingOptions.
     *
     * @param filePath путь к файлу
     * @param options параметры обработки в виде объекта FileReadingOptions
     * @return список строк с данными
     */
    default List<Map<String, String>> readRawDataWithOptions(Path filePath, FileReadingOptions options) {
        // По умолчанию преобразуем в Map для обратной совместимости
        return readRawData(filePath, options != null ? options.toMap() : null);
    }

    /**
     * Проверяет базовую валидность файла.
     *
     * @param filePath путь к файлу
     * @return результат проверки (true - файл валиден)
     */
    boolean validateFile(Path filePath);

    /**
     * Читает сырые данные из файла без преобразования в сущности.
     *
     * @param filePath путь к файлу
     * @param params дополнительные параметры обработки
     * @return список строк с данными
     */
    List<Map<String, String>> readRawData(Path filePath, Map<String, String> params);

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