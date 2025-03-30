package my.java.service.file.exporter.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.exporter.FileFormat;
import org.springframework.stereotype.Component;

/**
 * Фабрика для создания процессоров экспорта файлов
 * Путь: /java/my/java/service/file/exporter/processor/FileExportProcessorFactory.java
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileExportProcessorFactory {

    private final CsvFileExportProcessor csvProcessor;
    private final ExcelFileExportProcessor excelProcessor;


    /**
     * Создает процессор экспорта в зависимости от формата файла
     *
     * @param fileFormat формат файла
     * @return соответствующий процессор экспорта
     */
    @SuppressWarnings("unchecked")
    public <T extends ImportableEntity> FileExportProcessor<T> createProcessor(String fileFormat) {
        try {
            FileFormat format = FileFormat.fromString(fileFormat);
            return createProcessor(format);
        } catch (Exception e) {
            log.error("Неизвестный формат файла: {}. Используется CSV по умолчанию.", fileFormat);
            return (FileExportProcessor<T>) csvProcessor;
        }
    }

    /**
     * Создает процессор экспорта в зависимости от формата файла
     *
     * @param fileFormat формат файла
     * @return соответствующий процессор экспорта
     */
    @SuppressWarnings("unchecked")
    public <T extends ImportableEntity> FileExportProcessor<T> createProcessor(FileFormat fileFormat) {
        switch (fileFormat) {
            case CSV:
                return (FileExportProcessor<T>) csvProcessor;
            case EXCEL:
                return (FileExportProcessor<T>) excelProcessor;
            case JSON:
                // TODO: Реализация JSON-процессора
                log.warn("JSON-экспорт пока не реализован. Используется CSV по умолчанию.");
                return (FileExportProcessor<T>) csvProcessor;
            case XML:
                // TODO: Реализация XML-процессора
                log.warn("XML-экспорт пока не реализован. Используется CSV по умолчанию.");
                return (FileExportProcessor<T>) csvProcessor;
            default:
                log.warn("Неизвестный формат файла: {}. Используется CSV по умолчанию.", fileFormat);
                return (FileExportProcessor<T>) csvProcessor;
        }
    }
}