package my.java.service.file.importer;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileImportDto;
import my.java.dto.ImportResultDto;
import my.java.exception.FileOperationException;
import my.java.model.FieldMappingTemplate;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.repository.FileOperationRepository;
import my.java.service.client.ClientService;
import my.java.service.mapping.FieldMappingService;
import my.java.service.notification.ImportProgressNotifier;
import my.java.util.PathResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для асинхронного импорта CSV файлов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CsvImportService {

    private final PathResolver pathResolver;
    private final ClientService clientService;
    private final FileOperationRepository fileOperationRepository;
    private final FieldMappingService mappingService;
    private final ImportProgressNotifier progressNotifier;
    private final EntityManager entityManager;

    @Value("${import.batch.size:100}")
    private int defaultBatchSize;

//    /**
//     * Запускает асинхронный импорт CSV файла
//     */
//    @Async("fileProcessingExecutor")
//    @Transactional
//    public void importCsvAsync(FileImportDto importDto, FileOperation operation) {
//        log.info("Начало асинхронного импорта файла: {}", importDto.getFile().getOriginalFilename());
//
//        Path tempFile = null;
//        try {
//            // Сохраняем файл во временную директорию
//            tempFile = pathResolver.saveToTempFile(importDto.getFile(), "import");
//
//            // Обновляем статус операции
//            operation.setSourceFilePath(tempFile.toString());
//            operation.markAsProcessing();
//            fileOperationRepository.save(operation);
//
//            // Загружаем шаблон маппинга
//            FieldMappingTemplate template = mappingService
//                    .getTemplateWithRules(importDto.getMappingTemplateId())
//                    .orElseThrow(() -> new FileOperationException("Шаблон маппинга не найден"));
//
//            // Импортируем данные
//            ImportResultDto result = processCsvImport(
//                    tempFile,
//                    importDto,
//                    template,
//                    operation
//            );
//
//            // Обновляем операцию с результатами
//            updateOperationWithResults(operation, result);
//
//            // Перемещаем файл в директорию импорта
//            Path importedFile = pathResolver.moveFromTempToImport(tempFile, "imported");
//            operation.setResultFilePath(importedFile.toString());
//
//        } catch (Exception e) {
//            log.error("Ошибка при импорте файла: {}", e.getMessage(), e);
//            operation.markAsFailed(e.getMessage());
//            progressNotifier.notifyError(operation.getId(), e.getMessage());
//        } finally {
//            fileOperationRepository.save(operation);
//            // Очищаем временный файл если он остался
//            if (tempFile != null && pathResolver.fileExists(tempFile)) {
//                pathResolver.deleteFile(tempFile);
//            }
//        }
//    }

    /**
     * Запускает асинхронный импорт CSV файла из пути
     */
    @Async("fileProcessingExecutor")
    @Transactional
    public void importCsvFromPath(Path filePath, FileImportDto importDto, FileOperation operation) {
        log.info("Начало асинхронного импорта файла из пути: {}", filePath);

        try {
            // Обновляем статус операции
            operation.setSourceFilePath(filePath.toString());
            operation.markAsProcessing();
            fileOperationRepository.save(operation);

            // Загружаем шаблон маппинга
            FieldMappingTemplate template = mappingService
                    .getTemplateWithRules(importDto.getMappingTemplateId())
                    .orElseThrow(() -> new FileOperationException("Шаблон маппинга не найден"));

            // Импортируем данные
            ImportResultDto result = processCsvImport(
                    filePath,
                    importDto,
                    template,
                    operation
            );

            // Обновляем операцию с результатами
            updateOperationWithResults(operation, result);

            // Перемещаем файл в директорию импорта
            Path importedFile = pathResolver.moveFromTempToImport(filePath, "imported");
            operation.setResultFilePath(importedFile.toString());

        } catch (Exception e) {
            log.error("Ошибка при импорте файла: {}", e.getMessage(), e);
            operation.markAsFailed(e.getMessage());
            progressNotifier.notifyError(operation.getId(), e.getMessage());
        } finally {
            fileOperationRepository.save(operation);
        }
    }

    /**
     * Обрабатывает импорт CSV файла
     */
    private ImportResultDto processCsvImport(
            Path filePath,
            FileImportDto importDto,
            FieldMappingTemplate template,
            FileOperation operation) throws Exception {

        Charset charset = Charset.forName(importDto.getEncoding());
        char delimiter = importDto.getDelimiter().charAt(0);
        char quoteChar = importDto.getQuoteChar().charAt(0);
        int batchSize = importDto.getBatchSize() != null ? importDto.getBatchSize() : defaultBatchSize;

        AtomicInteger totalRows = new AtomicInteger(0);
        AtomicInteger processedRows = new AtomicInteger(0);
        AtomicInteger successRows = new AtomicInteger(0);
        AtomicInteger errorRows = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();
        Map<String, Integer> errorStats = new HashMap<>();

        // Настраиваем парсер CSV
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(delimiter)
                .withQuoteChar(quoteChar)
                .build();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath.toFile()), charset));
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(parser)
                     .build()) {

            // Читаем заголовки
            String[] headers = null;
            if (importDto.getHasHeader()) {
                headers = csvReader.readNext();
                if (headers == null) {
                    throw new FileOperationException("Файл пуст или не содержит заголовков");
                }
            }

            // Пакетная обработка
            List<ImportableEntity> batch = new ArrayList<>();
            String[] row;

            while ((row = csvReader.readNext()) != null) {
                totalRows.incrementAndGet();

                // Пропускаем пустые строки если нужно
                if (importDto.getSkipEmptyLines() && isEmptyRow(row)) {
                    continue;
                }

                try {
                    // Создаем map из строки
                    Map<String, String> rowData = createRowMap(headers, row, importDto.getTrimValues());

                    // Применяем маппинг
                    ImportableEntity entity = mappingService.applyMapping(
                            template,
                            rowData,
                            getEntityClass(template.getEntityType())
                    );

                    // Устанавливаем клиента
                    if (entity instanceof Product) {
                        ((Product) entity).setClientId(importDto.getClientId());
                    }

                    batch.add(entity);

                    // Сохраняем пакет при достижении размера
                    if (batch.size() >= batchSize) {
                        saveBatch(batch);
                        batch.clear();
                    }

                    successRows.incrementAndGet();

                } catch (Exception e) {
                    errorRows.incrementAndGet();
                    String errorMsg = String.format("Строка %d: %s", totalRows.get(), e.getMessage());
                    errors.add(errorMsg);

                    // Статистика ошибок
                    String errorType = e.getClass().getSimpleName();
                    errorStats.merge(errorType, 1, Integer::sum);

                    if (errors.size() < 100) { // Ограничиваем количество сохраняемых ошибок
                        log.warn(errorMsg);
                    }
                }

                processedRows.incrementAndGet();

                // Обновляем прогресс каждые N строк
                if (processedRows.get() % 100 == 0) {
                    updateProgress(operation, processedRows.get(), totalRows.get());
                }
            }

            // Сохраняем последний пакет
            if (!batch.isEmpty()) {
                saveBatch(batch);
            }

        }

        return ImportResultDto.builder()
                .operationId(operation.getId())
                .status("COMPLETED")
                .totalRows(totalRows.get())
                .processedRows(processedRows.get())
                .successRows(successRows.get())
                .errorRows(errorRows.get())
                .errors(errors)
                .errorStatistics(errorStats)
                .build();
    }

    /**
     * Сохраняет пакет сущностей
     */
    private void saveBatch(List<ImportableEntity> batch) {
        for (ImportableEntity entity : batch) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear(); // Очищаем контекст для экономии памяти
    }

    /**
     * Обновляет прогресс операции
     */
    private void updateProgress(FileOperation operation, int processed, int total) {
        int progress = total > 0 ? (processed * 100 / total) : 0;
        operation.setProcessedRecords(processed);
        operation.setTotalRecords(total);
        operation.setProcessingProgress(progress);
        fileOperationRepository.save(operation);

        // Уведомляем через WebSocket
        progressNotifier.notifyProgress(operation.getId(), progress, processed, total);
    }

    /**
     * Обновляет операцию с результатами импорта
     */
    private void updateOperationWithResults(FileOperation operation, ImportResultDto result) {
        operation.markAsCompleted(result.getSuccessRows());
        operation.setTotalRecords(result.getTotalRows());
        operation.setProcessedRecords(result.getProcessedRows());

        if (result.getErrorRows() > 0) {
            operation.setErrorMessage(String.format(
                    "Импорт завершен с ошибками. Успешно: %d, Ошибок: %d",
                    result.getSuccessRows(), result.getErrorRows()
            ));
        }
    }

    /**
     * Создает map из заголовков и значений строки
     */
    private Map<String, String> createRowMap(String[] headers, String[] values, Boolean trimValues) {
        Map<String, String> map = new HashMap<>();

        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
            String value = values[i];
            if (trimValues != null && trimValues && value != null) {
                value = value.trim();
            }
            map.put(headers[i], value);
        }

        return map;
    }

    /**
     * Проверяет, является ли строка пустой
     */
    private boolean isEmptyRow(String[] row) {
        return row == null || Arrays.stream(row).allMatch(v -> v == null || v.trim().isEmpty());
    }

    /**
     * Получает класс сущности по имени
     */
    @SuppressWarnings("unchecked")
    private Class<? extends ImportableEntity> getEntityClass(String entityType) {
        try {
            String className = "my.java.model.entity." + entityType;
            return (Class<? extends ImportableEntity>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Неизвестный тип сущности: " + entityType);
        }
    }
}