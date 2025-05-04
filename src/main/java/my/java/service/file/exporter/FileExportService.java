// src/main/java/my/java/service/file/exporter/FileExportService.java
package my.java.service.file.exporter;

import my.java.dto.FileOperationDto;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.service.file.options.FileWritingOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface FileExportService {
    /**
     * Асинхронно экспортирует данные в файл
     */
    CompletableFuture<FileOperationDto> exportDataAsync(
            Client client,
            String entityType,
            List<String> fields,
            Map<String, Object> filterParams,
            FileWritingOptions options);

    /**
     * Экспортирует данные напрямую (синхронно) без сохранения операции в БД
     * Используется для немедленного скачивания файла
     */
    Path exportDirectly(
            Client client,
            String entityType,
            List<String> fields,
            Map<String, Object> filterParams,
            FileWritingOptions options,
            FileOperation tempOperation);

    /**
     * Получает статус операции экспорта
     */
    FileOperationDto getExportStatus(Long operationId);

    /**
     * Отменяет операцию экспорта
     */
    boolean cancelExport(Long operationId);

    /**
     * Получает список доступных стратегий экспорта
     */
    List<Map<String, Object>> getAvailableStrategies();
}