package my.java.service.file.importer;

import my.java.dto.FileOperationDto;
import my.java.model.Client;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.options.FileReadingOptions;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис для импорта данных из файлов.
 * Обеспечивает функциональность загрузки файлов, их обработки
 * и сохранения данных в БД.
 */
public interface FileImportService {

    /**
     * Асинхронно обрабатывает загруженный файл и сохраняет результаты в БД
     * с использованием объекта FileReadingOptions для параметров.
     *
     * @param file       загруженный файл
     * @param client     клиент, для которого осуществляется импорт
     * @param mappingId  идентификатор маппинга полей (может быть null для автоопределения)
     * @param strategyId идентификатор стратегии обработки (может быть null для стратегии по умолчанию)
     * @param options    объект с параметрами обработки
     * @return объект Future с DTO операции импорта
     */
    CompletableFuture<FileOperationDto> importFileAsyncWithOptions(
            MultipartFile file,
            Client client,
            Long mappingId,
            Long strategyId,
            FileReadingOptions options);

    /**
     * Обрабатывает уже загруженный файл с использованием FileReadingOptions.
     *
     * @param filePath   путь к файлу
     * @param client     клиент, для которого осуществляется импорт
     * @param mappingId  идентификатор маппинга полей
     * @param strategyId идентификатор стратегии обработки
     * @param options    параметры обработки
     * @return DTO созданной операции
     */
    FileOperationDto processUploadedFileWithOptions(
            Path filePath,
            Client client,
            Long mappingId,
            Long strategyId,
            FileReadingOptions options);

    /**
     * Анализирует файл и определяет его структуру с использованием FileReadingOptions.
     *
     * @param file    загруженный файл
     * @param options параметры анализа
     * @return информация о структуре файла (заголовки, типы данных и т.д.)
     */
    Map<String, Object> analyzeFileWithOptions(MultipartFile file, FileReadingOptions options);

    /**
     * Асинхронно обрабатывает загруженный файл и сохраняет результаты в БД.
     *
     * @param file        загруженный файл
     * @param client      клиент, для которого осуществляется импорт
     * @param mappingId   идентификатор маппинга полей (может быть null для автоопределения)
     * @param strategyId  идентификатор стратегии обработки (может быть null для стратегии по умолчанию)
     * @param params      дополнительные параметры для обработки
     * @param isComposite флаг, указывающий, что импортируется составная сущность
     * @return объект Future с DTO операции импорта
     */
    CompletableFuture<FileOperationDto> importFileAsync(
            MultipartFile file,
            Client client,
            Long mappingId,
            Long strategyId,
            Map<String, String> params,
            boolean isComposite);

    /**
     * Возвращает статус операции импорта файла.
     *
     * @param operationId идентификатор операции импорта
     * @return DTO операции с обновленным статусом и прогрессом
     */
    FileOperationDto getImportStatus(Long operationId);

    /**
     * Анализирует файл и определяет его структуру.
     *
     * @param file загруженный файл
     * @return информация о структуре файла (заголовки, типы данных и т.д.)
     */
    Map<String, Object> analyzeFile(MultipartFile file);

    /**
     * Получает список доступных маппингов полей для клиента и типа сущности.
     *
     * @param clientId   идентификатор клиента
     * @param entityType тип сущности
     * @return список доступных маппингов
     */
    List<Map<String, Object>> getAvailableMappings(Long clientId, String entityType);

    /**
     * Получает список доступных стратегий обработки.
     *
     * @param fileType тип файла (CSV, EXCEL и т.д.)
     * @return список доступных стратегий
     */
    List<Map<String, Object>> getAvailableStrategies(String fileType);

    /**
     * Отменяет выполняющуюся операцию импорта.
     *
     * @param operationId идентификатор операции импорта
     * @return true, если операция успешно отменена
     */
    boolean cancelImport(Long operationId);

    /**
     * Обрабатывает уже загруженный файл.
     *
     * @param filePath   путь к файлу
     * @param client     клиент, для которого осуществляется импорт
     * @param mappingId  идентификатор маппинга полей
     * @param strategyId идентификатор стратегии обработки
     * @param params     дополнительные параметры для обработки
     * @return DTO созданной операции
     */
    FileOperationDto processUploadedFile(
            Path filePath,
            Client client,
            Long mappingId,
            Long strategyId,
            Map<String, String> params);

    /**
     * Возвращает экземпляр сущности указанного типа.
     *
     * @param entityType тип сущности
     * @return созданный экземпляр сущности или null, если тип не поддерживается
     */
    ImportableEntity createEntityInstance(String entityType);
}