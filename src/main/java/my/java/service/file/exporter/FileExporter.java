// src/main/java/my/java/service/file/exporter/FileExporter.java
package my.java.service.file.exporter;

import my.java.model.FileOperation;
import my.java.service.file.options.FileWritingOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс для экспорта данных в файл
 */
public interface FileExporter {
    /**
     * Поддерживаемые типы файлов
     */
    String[] getSupportedFileTypes();

    /**
     * Экспортирует данные в файл
     */
    Path exportData(
            List<Map<String, String>> data,
            List<String> fields,
            FileWritingOptions options,
            FileOperation operation
    );

    /**
     * Проверяет, подходит ли экспортер для заданного типа файла
     */
    boolean canExport(String fileType);
}