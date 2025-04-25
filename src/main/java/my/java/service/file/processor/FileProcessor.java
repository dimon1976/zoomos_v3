// src/main/java/my/java/service/file/processor/FileProcessor.java
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
 */
public interface FileProcessor {

    String[] getSupportedFileExtensions();

    String[] getSupportedMimeTypes();

    boolean canProcess(Path filePath);

    /**
     * Анализирует файл и возвращает информацию о его структуре.
     */
    Map<String, Object> analyzeFileWithOptions(Path filePath, FileReadingOptions options);

    ImportableEntity createEntity(String entityType);

    /**
     * Обрабатывает файл и создает список сущностей из его данных.
     */
    List<ImportableEntity> processFileWithOptions(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            FileReadingOptions options,
            FileOperation operation);

    /**
     * Читает сырые данные из файла.
     */
    List<Map<String, String>> readRawDataWithOptions(Path filePath, FileReadingOptions options);

    /**
     * Проверяет базовую валидность файла.
     */
    boolean validateFile(Path filePath);

    /**
     * Оценивает количество записей в файле без полной его обработки.
     */
    int estimateRecordCount(Path filePath);

    /**
     * Определяет, поддерживает ли процессор обработку потоком (для больших файлов).
     */
    boolean supportsStreaming();

    /**
     * Получает дополнительные параметры конфигурации, которые может принимать процессор.
     */
    Map<String, Object> getConfigParameters();
}