// src/main/java/my/java/service/file/importer/FileImportServiceImpl.java
package my.java.service.file.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.repository.FileOperationRepository;
import my.java.service.entity.competitor.CompetitorService;
import my.java.service.entity.product.ProductService;
import my.java.service.entity.region.RegionService;
import my.java.service.file.entity.CompositeEntityService;
import my.java.service.file.mapping.FieldMappingService;
import my.java.service.file.options.FileReadingOptions;
import my.java.service.file.processor.FileProcessor;
import my.java.service.file.processor.FileProcessorFactory;
import my.java.service.file.strategy.FileProcessingStrategy;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реализация сервиса импорта файлов.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileImportServiceImpl implements FileImportService {

    private final PathResolver pathResolver;
    private final FileOperationRepository fileOperationRepository;
    private final FileProcessorFactory fileProcessorFactory;
    private final FieldMappingService fieldMappingService;
    private final ProductService productService;
    private final RegionService regionService;
    private final CompetitorService competitorService;
    @Autowired
    private CompositeEntityService compositeEntityService;

    @Qualifier("fileProcessingExecutor")
    private final TaskExecutor fileProcessingExecutor;
    private final List<FileProcessingStrategy> processingStrategies;
    private final Map<Long, CompletableFuture<FileOperationDto>> activeImportTasks = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> analyzeFileWithOptions(MultipartFile file, FileReadingOptions options) {
        try {
            // Сохраняем файл во временную директорию
            Path tempFilePath = pathResolver.saveToTempFile(file, "analyze");

            // Получаем подходящий процессор и анализируем файл
            FileProcessor processor = fileProcessorFactory.createProcessor(tempFilePath)
                    .orElseThrow(() -> new FileOperationException("Не найден подходящий процессор для файла"));

            Map<String, Object> result = processor.analyzeFileWithOptions(tempFilePath, options);
            result.put("processorType", processor.getClass().getSimpleName());
            result.put("options", options);

            // Удаляем временный файл
            pathResolver.deleteFile(tempFilePath);
            return result;
        } catch (IOException e) {
            throw new FileOperationException("Не удалось проанализировать файл: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<FileOperationDto> importFileAsyncWithOptions(
            MultipartFile file,
            Client client,
            Long mappingId,
            Long strategyId,
            FileReadingOptions options) {

        try {
            // Сохраняем файл во временную директорию
            Path tempFilePath = pathResolver.saveToTempFile(file, "import_" + client.getId());

            // Создаем запись об операции в БД
            FileOperation operation = createFileOperation(client, file, tempFilePath);

            // Запускаем асинхронную обработку
            CompletableFuture<FileOperationDto> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return processUploadedFileWithOptions(tempFilePath, client, mappingId, strategyId, options);
                } catch (Exception e) {
                    operation.markAsFailed(e.getMessage());
                    fileOperationRepository.save(operation);
                    throw new RuntimeException("Ошибка при обработке файла: " + e.getMessage(), e);
                }
            }, fileProcessingExecutor);

            // Сохраняем и настраиваем обработку завершения
            activeImportTasks.put(operation.getId(), future);
            future.whenComplete((result, ex) -> activeImportTasks.remove(operation.getId()));

            return future;
        } catch (IOException e) {
            throw new FileOperationException("Не удалось сохранить файл: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public FileOperationDto processUploadedFileWithOptions(
            Path filePath,
            Client client,
            Long mappingId,
            Long strategyId,
            FileReadingOptions options) {

        // Создаем запись об операции, если еще не создана
        FileOperation operation = findOrCreateFileOperation(client, filePath);

        try {
            // Валидируем параметры
            options.validate();

            // Получаем подходящий процессор для файла
            FileProcessor processor = fileProcessorFactory.createProcessor(filePath)
                    .orElseThrow(() -> new FileOperationException("Не найден подходящий процессор для файла"));

            // Определяем параметры обработки
            String entityType = options.getAdditionalParam("entityType", "product");
            boolean isComposite = Boolean.parseBoolean(options.getAdditionalParam("composite", "false"));
            options.updateStrategy(entityType, client.getId());

            // Получаем маппинг полей
            Map<String, String> fieldMapping = getMappingFields(mappingId, processor, options, entityType);

            // Меняем статус операции
            operation.markAsProcessing();
            operation = fileOperationRepository.save(operation);

            // Добавляем информацию об операции
            options.getAdditionalParams().put("operationId", operation.getId().toString());
            options.getAdditionalParams().put("totalRecords", String.valueOf(operation.getTotalRecords()));

            // Обрабатываем данные
            List<ImportableEntity> entities = isComposite
                    ? processCompositeEntitiesWithOptions(processor, filePath, entityType, client, fieldMapping, options, operation)
                    : processor.processFileWithOptions(filePath, entityType, client, fieldMapping, options, operation);

            // Сохраняем сущности в БД
            int savedEntities = saveEntities(entities, entityType);

            // Обновляем статус
            operation.markAsCompleted(savedEntities);
            operation = fileOperationRepository.save(operation);

            // Перемещаем файл из временной директории в постоянную
            Path permanentPath = pathResolver.moveFromTempToUpload(filePath, "imported_" + client.getId());
            operation.setResultFilePath(permanentPath.toString());
            operation = fileOperationRepository.save(operation);

            return mapToDto(operation);
        } catch (Exception e) {
            operation.markAsFailed(e.getMessage());
            operation = fileOperationRepository.save(operation);
            throw new FileOperationException("Ошибка при обработке файла: " + e.getMessage(), e);
        }
    }

    /**
     * Получает маппинг полей для импорта
     */
    private Map<String, String> getMappingFields(
            Long mappingId,
            FileProcessor processor,
            FileReadingOptions options,
            String entityType) throws FileOperationException {

        Map<String, String> fieldMapping = mappingId != null
                ? fieldMappingService.getMappingById(mappingId)
                : null;

        return fieldMapping;
    }

    @Override
    public FileOperationDto getImportStatus(Long operationId) {
        Optional<FileOperation> operationOpt = fileOperationRepository.findById(operationId);
        if (operationOpt.isEmpty()) {
            throw new FileOperationException("Операция с ID " + operationId + " не найдена");
        }

        FileOperation operation = operationOpt.get();

        // Проверяем активные задачи
        CompletableFuture<FileOperationDto> future = activeImportTasks.get(operationId);
        if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
            try {
                return future.get();
            } catch (Exception e) {
                log.error("Ошибка при получении результата задачи: {}", e.getMessage());
            }
        }

        return mapToDto(operation);
    }

    @Override
    public List<Map<String, Object>> getAvailableMappings(Long clientId, String entityType) {
        return fieldMappingService.getAvailableMappingsForClient(clientId, entityType);
    }

    @Override
    public List<Map<String, Object>> getAvailableStrategies(String fileType) {
        List<Map<String, Object>> strategies = new ArrayList<>();

        for (FileProcessingStrategy strategy : processingStrategies) {
            if (strategy.getSupportedFileTypes().contains(fileType)) {
                strategies.add(Map.of(
                        "id", strategy.getStrategyId(),
                        "name", strategy.getDisplayName(),
                        "description", strategy.getDescription(),
                        "parameters", strategy.getConfigurableParameters()
                ));
            }
        }

        return strategies;
    }

    @Override
    public boolean cancelImport(Long operationId) {
        CompletableFuture<FileOperationDto> future = activeImportTasks.get(operationId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);

            if (cancelled) {
                // Обновляем статус операции
                fileOperationRepository.findById(operationId).ifPresent(operation -> {
                    operation.markAsFailed("Операция отменена пользователем");
                    fileOperationRepository.save(operation);
                });

                activeImportTasks.remove(operationId);
                return true;
            }
        }

        return false;
    }

    /**
     * Обрабатывает составные сущности
     */
    private List<ImportableEntity> processCompositeEntitiesWithOptions(
            FileProcessor processor,
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            FileReadingOptions options,
            FileOperation operation) {

        // Получаем сырые данные
        List<Map<String, String>> rawData = processor.readRawDataWithOptions(filePath, options);
        if (operation != null) {
            operation.setTotalRecords(rawData.size());
        }

        // Обрабатываем данные
        List<ImportableEntity> resultEntities = new ArrayList<>();
        int processedRecords = 0;
        String errorHandling = options.getErrorHandling();

        for (Map<String, String> row : rawData) {
            processedRecords++;

            try {
                // Обрабатываем строку и добавляем результаты
                List<ImportableEntity> entitiesFromRow = compositeEntityService.processRowWithOptions(
                        applyMapping(row, fieldMapping), fieldMapping, client.getId(), options);
                resultEntities.addAll(entitiesFromRow);
            } catch (Exception e) {
                if ("stop".equals(errorHandling)) {
                    throw new FileOperationException("Ошибка при обработке строки " + processedRecords + ": " + e.getMessage());
                }
            }

            // Обновляем прогресс
            updateProgress(operation, processedRecords, rawData.size());
        }

        compositeEntityService.clearCache();
        return resultEntities;
    }

    /**
     * Применяет маппинг полей к данным
     */
    private Map<String, String> applyMapping(Map<String, String> rawData, Map<String, String> fieldMapping) {
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            return new HashMap<>(rawData);
        }

        Map<String, String> result = new HashMap<>();

        fieldMapping.forEach((fileHeader, entityField) -> {
            if (rawData.containsKey(fileHeader)) {
                result.put(entityField, rawData.get(fileHeader));
            }
        });

        return result;
    }

    /**
     * Сохраняет сущности в БД
     */
    @Transactional
    public int saveEntities(List<ImportableEntity> entities, String entityType) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        switch (entityType.toLowerCase()) {
            case "product":
                return productService.saveProducts(entities.stream()
                        .filter(e -> e instanceof Product)
                        .map(e -> (Product) e)
                        .toList());

            case "region":
                return regionService.saveRegionList(entities.stream()
                        .filter(e -> e instanceof Region)
                        .map(e -> (Region) e)
                        .toList());

            case "competitor":
                return competitorService.saveCompetitorList(entities.stream()
                        .filter(e -> e instanceof Competitor)
                        .map(e -> (Competitor) e)
                        .toList());

            default:
                return 0;
        }
    }

    /**
     * Создает запись об операции импорта файла
     */
    private FileOperation createFileOperation(Client client, MultipartFile file, Path filePath) {
        FileOperation operation = new FileOperation();
        operation.setClient(client);
        operation.setOperationType(FileOperation.OperationType.IMPORT);
        operation.setFileName(file.getOriginalFilename());
        operation.setFileType(getFileType(file));
        operation.setStatus(FileOperation.OperationStatus.PENDING);
        operation.setSourceFilePath(filePath.toString());
        operation.setFileSize(file.getSize());
        return fileOperationRepository.save(operation);
    }

    /**
     * Ищет существующую операцию или создает новую
     */
    private FileOperation findOrCreateFileOperation(Client client, Path filePath) {
        // Ищем существующую операцию
        List<FileOperation> pendingOps = fileOperationRepository.findByClientIdAndStatus(
                client.getId(), FileOperation.OperationStatus.PENDING);

        for (FileOperation op : pendingOps) {
            if (filePath.toString().equals(op.getSourceFilePath())) {
                return op;
            }
        }

        // Создаем новую
        FileOperation operation = new FileOperation();
        operation.setClient(client);
        operation.setOperationType(FileOperation.OperationType.IMPORT);
        operation.setFileName(filePath.getFileName().toString());
        operation.setFileType(getFileTypeFromPath(filePath));
        operation.setStatus(FileOperation.OperationStatus.PENDING);
        operation.setSourceFilePath(filePath.toString());
        operation.setFileSize(pathResolver.getFileSize(filePath));
        operation.setStartedAt(ZonedDateTime.now());
        return fileOperationRepository.save(operation);
    }

    /**
     * Определяет тип файла по MultipartFile
     */
    private String getFileType(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex > 0) {
                return originalFilename.substring(lastDotIndex + 1).toUpperCase();
            }
        }

        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.contains("csv")) return "CSV";
            if (contentType.contains("excel") || contentType.contains("spreadsheet")) return "EXCEL";
        }

        return "UNKNOWN";
    }

    /**
     * Определяет тип файла по пути
     */
    private String getFileTypeFromPath(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toUpperCase() : "UNKNOWN";
    }

    /**
     * Обновляет прогресс операции
     */
    private void updateProgress(FileOperation operation, int processedRecords, int totalRecords) {
        if (operation != null && totalRecords > 0) {
            int progress = (int) (((double) processedRecords / totalRecords) * 100);
            operation.setProcessingProgress(progress);
            operation.setProcessedRecords(processedRecords);
        }
    }

    /**
     * Преобразует модель операции в DTO
     */
    private FileOperationDto mapToDto(FileOperation operation) {
        if (operation == null) return null;

        return FileOperationDto.builder()
                .id(operation.getId())
                .clientId(operation.getClient() != null ? operation.getClient().getId() : null)
                .operationType(operation.getOperationType())
                .fileName(operation.getFileName())
                .fileType(operation.getFileType())
                .recordCount(operation.getRecordCount())
                .status(operation.getStatus())
                .errorMessage(operation.getErrorMessage())
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .build();
    }

    @Override
    public ImportableEntity createEntityInstance(String entityType) {
        if (entityType == null) return null;

        ImportableEntity entity = switch (entityType.toLowerCase()) {
            case "product" -> new Product();
            case "regiondata" -> new Region();
            case "competitordata" -> new Competitor();
            default -> null;
        };

        if (entity != null) {
            try {
                entity.getClass().getMethod("setTransformerFactory", ValueTransformerFactory.class)
                        .invoke(entity);
            } catch (Exception ignored) {
                // Игнорируем, если метод не поддерживается
            }
        }

        return entity;
    }
}