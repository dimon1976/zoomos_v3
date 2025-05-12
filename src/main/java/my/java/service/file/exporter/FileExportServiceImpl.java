// src/main/java/my/java/service/file/exporter/FileExportServiceImpl.java
package my.java.service.file.exporter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import my.java.service.entity.EntityDataService;
import my.java.service.file.exporter.strategy.ExportProcessingStrategy;
import my.java.service.file.options.FileWritingOptions;
import my.java.util.PathResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileExportServiceImpl implements FileExportService {

    private final List<FileExporter> exporters;
    private final List<ExportProcessingStrategy> processingStrategies;
    private final EntityDataService entityDataService;
    private final FileOperationRepository fileOperationRepository;
    private final ObjectMapper objectMapper;
    private final PathResolver pathResolver;

    @Qualifier("fileProcessingExecutor")
    private final TaskExecutor fileProcessingExecutor;

    private final Map<Long, CompletableFuture<FileOperationDto>> activeExportTasks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<FileOperationDto> exportDataAsync(
            Client client,
            String entityType,
            List<String> fields,
            Map<String, Object> filterParams,
            FileWritingOptions options) {

        // Создаем запись об операции в БД
        FileOperation operation = createFileOperation(client, options.getFileType());

        // Запускаем асинхронную обработку
        CompletableFuture<FileOperationDto> future = CompletableFuture.supplyAsync(() -> {
            try {
                return exportData(client, entityType, fields, filterParams, options, operation);
            } catch (Exception e) {
                operation.markAsFailed(e.getMessage());
                fileOperationRepository.save(operation);
                throw new RuntimeException("Ошибка при экспорте данных: " + e.getMessage(), e);
            }
        }, fileProcessingExecutor);

        // Сохраняем и настраиваем обработку завершения
        activeExportTasks.put(operation.getId(), future);
        future.whenComplete((result, ex) -> activeExportTasks.remove(operation.getId()));

        return future;
    }

    /**
     * Экспортирует данные напрямую (синхронно) без сохранения операции в БД
     * Используется для немедленного скачивания файла
     */
    @Override
    public Path exportDirectly(
            Client client,
            String entityType,
            List<String> fields,
            Map<String, Object> filterParams,
            FileWritingOptions options,
            FileOperation tempOperation) {

        try {
            // Применяем пользовательский порядок полей, если он задан
            List<String> sortedFields = applyFieldOrder(fields, options);
            log.info("Поля после сортировки: {}", sortedFields);

            // ВАЖНО: Используем sortedFields вместо fields!
            // Получаем данные для экспорта
            List<Map<String, String>> data = entityDataService.getEntityDataForExport(
                    entityType, sortedFields, filterParams, client.getId());

            if (data.isEmpty()) {
                throw new FileOperationException("Нет данных для экспорта");
            }

            log.info("Получено {} записей для прямого экспорта", data.size());

            // Получаем стратегию
            String strategyId = options.getAdditionalParams().getOrDefault("strategyId", "simple");
            ExportProcessingStrategy strategy = findStrategy(strategyId);

            // Обрабатываем данные
            List<Map<String, String>> processedData = strategy.processData(data, sortedFields, options.getAdditionalParams());

            // Получаем экспортер для выбранного формата
            FileExporter exporter = findExporter(options.getFileType());

            // Экспортируем данные во временный файл с использованием sortedFields!
            return exporter.exportData(processedData, sortedFields, options, tempOperation);

        } catch (Exception e) {
            log.error("Ошибка при прямом экспорте данных: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при экспорте данных: " + e.getMessage(), e);
        }
    }


    @Transactional
    public FileOperationDto exportData(
            Client client,
            String entityType,
            List<String> fields,
            Map<String, Object> filterParams,
            FileWritingOptions options,
            FileOperation operation) {

        Map<String, Object> statistics = new HashMap<>();
        long startTime = System.currentTimeMillis();
        Path tempFilePath = null;
        Path permanentFilePath = null;

        try {
            // Обновляем статус операции
            operation.markAsProcessing();
            operation = fileOperationRepository.save(operation);

            // Применяем пользовательский порядок полей, если он задан
            fields = applyFieldOrder(fields, options);

            // Этап 1: Получение данных
            operation.addStage("data_fetch", "Получение данных");
            operation.updateStageProgress("data_fetch", 0);

            log.info("Начало экспорта для клиента {}, тип сущности: {}", client.getId(), entityType);
            List<Map<String, String>> data = entityDataService.getEntityDataForExport(
                    entityType, fields, filterParams, client.getId());

            operation.updateStageProgress("data_fetch", 100);
            operation.completeStage("data_fetch");

            log.info("Получено {} записей для экспорта", data.size());
            statistics.put("totalRecords", data.size());

            // Проверяем наличие данных
            if (data.isEmpty()) {
                throw new FileOperationException("Нет данных для экспорта");
            }

            // Этап 2: Применение стратегии
            operation.addStage("processing", "Обработка данных");

            // Получаем стратегию
            String strategyId = options.getAdditionalParams().getOrDefault("strategyId", "simple");
            ExportProcessingStrategy strategy = findStrategy(strategyId);

            // Обрабатываем данные
            List<Map<String, String>> processedData = strategy.processData(data, fields, options.getAdditionalParams());

            operation.updateStageProgress("processing", 100);
            operation.completeStage("processing");

            statistics.put("processedRecords", processedData.size());

            // Этап 3: Экспорт данных
            // Получаем экспортер
            FileExporter exporter = findExporter(options.getFileType());

            // Экспортируем данные во временный файл
            tempFilePath = exporter.exportData(processedData, fields, options, operation);

            log.info("Файл успешно создан во временной директории: {}", tempFilePath.toAbsolutePath());

            // Этап 4: Перемещение файла из временной директории в постоянную
            operation.addStage("file_move", "Перемещение файла");

            // Формируем префикс имени файла с датой и ID клиента
            String filePrefix = client.getId() + "_" + entityType + "_export";

            // Перемещаем файл в директорию экспорта
            permanentFilePath = pathResolver.moveFromTempToExport(tempFilePath, filePrefix);

            log.info("Файл перемещен в директорию экспорта: {}", permanentFilePath.toAbsolutePath());

            operation.completeStage("file_move");

            // Обновляем информацию о файле
            statistics.put("fileName", operation.getFileName());
            statistics.put("fileType", operation.getFileType());
            statistics.put("fileSize", Files.size(permanentFilePath));
            statistics.put("filePath", permanentFilePath.toAbsolutePath().toString());

            // Обновляем статус операции
            operation.markAsCompleted(processedData.size());
            operation.setResultFilePath(permanentFilePath.toAbsolutePath().toString());

            // Сохраняем статистику
            long endTime = System.currentTimeMillis();
            statistics.put("totalTimeMs", endTime - startTime);

            try {
                operation.setAdditionalInfo(objectMapper.writeValueAsString(statistics));
            } catch (Exception e) {
                log.warn("Не удалось сохранить статистику: {}", e.getMessage());
            }

            operation = fileOperationRepository.save(operation);
            log.info("Экспорт завершен успешно, ID операции: {}", operation.getId());

            return FileOperationDto.fromEntity(operation);
        } catch (Exception e) {
            log.error("Ошибка при экспорте данных: {}", e.getMessage(), e);

            // Удаляем временный файл при ошибке, если он был создан
            if (tempFilePath != null && Files.exists(tempFilePath)) {
                try {
                    pathResolver.deleteFile(tempFilePath);
                    log.info("Временный файл удален: {}", tempFilePath);
                } catch (Exception ex) {
                    log.error("Не удалось удалить временный файл: {}", ex.getMessage());
                }
            }

            operation.markAsFailed(e.getMessage());

            // Определяем текущий этап и помечаем его как проваленный
            if (!operation.getStages().isEmpty()) {
                FileOperation.OperationStage currentStage = operation.getStages().stream()
                        .filter(s -> s.getStatus() == FileOperation.OperationStatus.PROCESSING)
                        .findFirst()
                        .orElse(operation.getStages().get(operation.getStages().size() - 1));

                operation.failStage(currentStage.getName(), e.getMessage());
            }

            fileOperationRepository.save(operation);
            throw new FileOperationException("Ошибка при экспорте данных: " + e.getMessage(), e);
        }
    }

    /**
     * Применяет пользовательский порядок полей, если он задан в параметрах
     * @param fields Исходный список полей
     * @param options Параметры экспорта
     * @return Отсортированный список полей
     */
    private List<String> applyFieldOrder(List<String> fields, FileWritingOptions options) {
        log.info("Применение порядка полей: исходный список: {}", fields);

        // Проверяем наличие порядка полей в дополнительных параметрах
        String fieldsOrderJson = options.getAdditionalParams().get("fieldsOrder");
        log.info("Порядок полей из параметров: {}", fieldsOrderJson);

        if (fieldsOrderJson != null && !fieldsOrderJson.isEmpty()) {
            try {
                // Преобразуем JSON в List
                List<String> orderedFields = objectMapper.readValue(
                        fieldsOrderJson, new TypeReference<List<String>>() {});

                log.info("Десериализованный порядок полей: {}", orderedFields);

                if (!orderedFields.isEmpty()) {
                    // Создаем новый список с заданным порядком
                    List<String> sortedFields = new ArrayList<>();

                    // Сначала добавляем поля в порядке из orderedFields
                    for (String field : orderedFields) {
                        if (fields.contains(field)) {
                            sortedFields.add(field);
                            log.debug("Добавлено поле по порядку: {}", field);
                        } else {
                            log.warn("Поле из порядка отсутствует в исходном списке: {}", field);
                        }
                    }

                    // Затем добавляем поля, которые есть в исходном списке, но отсутствуют в orderedFields
                    for (String field : fields) {
                        if (!sortedFields.contains(field)) {
                            sortedFields.add(field);
                            log.debug("Добавлено поле, отсутствующее в порядке: {}", field);
                        }
                    }

                    // Проверяем, отличается ли порядок от исходного
                    boolean isDifferent = !fields.equals(sortedFields);
                    if (isDifferent) {
                        log.info("Порядок полей был изменен: было {} -> стало {}", fields, sortedFields);
                    } else {
                        log.info("Порядок полей не изменился");
                    }

                    // Заменяем исходный список отсортированным
                    return new ArrayList<>(sortedFields); // Создаем новый список
                } else {
                    log.warn("Десериализованный список порядка полей пуст");
                }
            } catch (Exception e) {
                log.warn("Ошибка при применении порядка полей: {}", e.getMessage(), e);
            }
        } else {
            log.info("Параметр порядка полей отсутствует или пуст");
        }

        // Если порядок не задан или произошла ошибка, возвращаем исходный список
        return new ArrayList<>(fields); // Возвращаем копию исходного списка
    }

    @Override
    public FileOperationDto getExportStatus(Long operationId) {
        Optional<FileOperation> operationOpt = fileOperationRepository.findById(operationId);
        if (operationOpt.isEmpty()) {
            throw new FileOperationException("Операция с ID " + operationId + " не найдена");
        }

        FileOperation operation = operationOpt.get();

        // Проверяем активные задачи
        CompletableFuture<FileOperationDto> future = activeExportTasks.get(operationId);
        if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
            try {
                return future.get();
            } catch (Exception e) {
                log.error("Ошибка при получении результата задачи: {}", e.getMessage());
            }
        }

        return FileOperationDto.fromEntity(operation);
    }

    @Override
    public boolean cancelExport(Long operationId) {
        CompletableFuture<FileOperationDto> future = activeExportTasks.get(operationId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);

            if (cancelled) {
                // Обновляем статус операции
                fileOperationRepository.findById(operationId).ifPresent(operation -> {
                    operation.markAsFailed("Операция отменена пользователем");
                    fileOperationRepository.save(operation);
                });

                activeExportTasks.remove(operationId);
                return true;
            }
        }

        return false;
    }

    @Override
    public List<Map<String, Object>> getAvailableStrategies() {
        return processingStrategies.stream()
                .map(strategy -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", strategy.getStrategyId());
                    result.put("name", strategy.getDisplayName());
                    result.put("description", strategy.getDescription());
                    return result;
                })
                .collect(Collectors.toList());
    }

    private FileOperation createFileOperation(Client client, String fileType) {
        FileOperation operation = new FileOperation();
        operation.setClient(client);
        operation.setOperationType(FileOperation.OperationType.EXPORT);
        operation.setFileName("export_" + System.currentTimeMillis() + "." + fileType.toLowerCase());
        operation.setFileType(fileType.toUpperCase());
        operation.setStatus(FileOperation.OperationStatus.PENDING);
        operation.setStartedAt(ZonedDateTime.now());
        return fileOperationRepository.save(operation);
    }

    private FileExporter findExporter(String fileType) {
        return exporters.stream()
                .filter(e -> e.canExport(fileType))
                .findFirst()
                .orElseThrow(() -> new FileOperationException("Не найден экспортер для формата " + fileType));
    }

    private ExportProcessingStrategy findStrategy(String strategyId) {
        return processingStrategies.stream()
                .filter(s -> s.getStrategyId().equals(strategyId))
                .findFirst()
                .orElseThrow(() -> new FileOperationException("Не найдена стратегия обработки " + strategyId));
    }
}