package my.java.service.file.exporter.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.exporter.ExportConfig;
import my.java.service.file.exporter.tracker.ExportProgressTracker;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Интерфейс процессора экспорта файлов
 * Путь: /java/my/java/service/file/exporter/processor/FileExportProcessor.java
 */
public interface FileExportProcessor<T extends ImportableEntity> {

    /**
     * Обрабатывает список сущностей и записывает результат в выходной поток
     *
     * @param entities список сущностей для экспорта
     * @param config конфигурация экспорта
     * @param outputStream поток для записи результатов
     * @param progressTracker трекер прогресса для обновления статуса
     * @param operationId идентификатор операции
     */
    void process(
            List<T> entities,
            ExportConfig config,
            OutputStream outputStream,
            ExportProgressTracker progressTracker,
            Long operationId);

    /**
     * Возвращает расширение файла для этого процессора
     */
    String getFileExtension();
}

