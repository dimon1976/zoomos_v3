package my.java.service.file.exporter;

import my.java.dto.FileOperationDto;
import my.java.model.Client;
import my.java.model.entity.ImportableEntity;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Сервис для экспорта данных в файлы
 * Путь: /java/my/java/service/file/exporter/FileExportService.java
 */
public interface FileExportService {

    /**
     * Экспортирует данные сущностей в файл указанного формата с применением фильтров
     *
     * @param entityClass класс экспортируемой сущности
     * @param client клиент, для которого выполняется экспорт
     * @param filterCriteria критерии фильтрации данных
     * @param outputStream поток для записи результатов
     * @param fileFormat формат файла (CSV, EXCEL и т.д.)
     * @param entityType тип сущности для записи в FileOperation
     * @return информация об операции экспорта
     */
    <T extends ImportableEntity> FileOperationDto exportData(
            Class<T> entityClass,
            Client client,
            Map<String, Object> filterCriteria,
            OutputStream outputStream,
            String fileFormat,
            String entityType);

    /**
     * Экспортирует данные, которые были импортированы из конкретного файла
     *
     * @param importOperationId идентификатор операции импорта
     * @param outputStream поток для записи результатов
     * @param fileFormat формат файла
     * @return информация об операции экспорта
     */
    FileOperationDto exportDataByImportOperation(
            Long importOperationId,
            OutputStream outputStream,
            String fileFormat);

    /**
     * Экспортирует указанный список сущностей в файл
     *
     * @param entities список сущностей для экспорта
     * @param client клиент, для которого выполняется экспорт
     * @param outputStream поток для записи результатов
     * @param fileFormat формат файла
     * @param entityType тип сущности для записи в FileOperation
     * @return информация об операции экспорта
     */
    <T extends ImportableEntity> FileOperationDto exportEntities(
            List<T> entities,
            Client client,
            OutputStream outputStream,
            String fileFormat,
            String entityType);

    /**
     * Получает статус операции экспорта
     *
     * @param operationId идентификатор операции
     * @return информация об операции экспорта
     */
    FileOperationDto getExportStatus(Long operationId);

    /**
     * Отменяет операцию экспорта
     *
     * @param operationId идентификатор операции
     * @return true, если операция успешно отменена
     */
    boolean cancelExport(Long operationId);
}