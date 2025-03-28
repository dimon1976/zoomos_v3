package my.java.service.file.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.CompetitorData;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.repository.FileOperationRepository;
import my.java.service.competitor.CompetitorDataService;
import my.java.service.file.builder.EntitySetBuilderFactory;
import my.java.service.file.entity.EntitySaverFactory;
import my.java.service.file.job.FileImportJob;
import my.java.service.file.mapping.FieldMappingService;
import my.java.service.file.processor.FileProcessor;
import my.java.service.file.processor.FileProcessorFactory;
import my.java.service.file.repository.RelatedEntitiesRepository;
import my.java.service.file.strategy.FileProcessingStrategy;
import my.java.service.file.tracker.ImportProgressTracker;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.service.product.ProductService;
import my.java.service.region.RegionDataService;
import my.java.util.PathResolver;
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
import java.util.stream.Collectors;

/**
 * Реализация сервиса импорта файлов.
 * Обеспечивает функциональность загрузки, обработки и сохранения данных из файлов.
 */
@Service
@Slf4j
public class FileImportServiceImpl implements FileImportService {

    private final PathResolver pathResolver;
    private final FileOperationRepository fileOperationRepository;
    private final FileProcessorFactory fileProcessorFactory;
    private final FieldMappingService fieldMappingService;
    private final ValueTransformerFactory transformerFactory;
    private final ProductService productService;
    private final RegionDataService regionDataService;
    private final CompetitorDataService competitorDataService;
    private final RelatedEntitiesRepository relatedEntitiesRepository;
    private final EntitySetBuilderFactory entitySetBuilderFactory;
    private final EntitySaverFactory entitySaverFactory;
    private final ImportProgressTracker importProgressTracker;


    // Новый явный конструктор
    public FileImportServiceImpl(
            PathResolver pathResolver,
            FileOperationRepository fileOperationRepository,
            FileProcessorFactory fileProcessorFactory,
            FieldMappingService fieldMappingService,
            ValueTransformerFactory transformerFactory,
            ProductService productService,
            RegionDataService regionDataService,
            CompetitorDataService competitorDataService,
            @Qualifier("fileProcessingExecutor") TaskExecutor fileProcessingExecutor,
            List<FileProcessingStrategy> processingStrategies,
            RelatedEntitiesRepository relatedEntitiesRepository,
            EntitySetBuilderFactory entitySetBuilderFactory, EntitySaverFactory entitySaverFactory, ImportProgressTracker importProgressTracker) {

        this.pathResolver = pathResolver;
        this.fileOperationRepository = fileOperationRepository;
        this.fileProcessorFactory = fileProcessorFactory;
        this.fieldMappingService = fieldMappingService;
        this.transformerFactory = transformerFactory;
        this.productService = productService;
        this.regionDataService = regionDataService;
        this.competitorDataService = competitorDataService;
        this.fileProcessingExecutor = fileProcessingExecutor;
        this.processingStrategies = processingStrategies;
        this.relatedEntitiesRepository = relatedEntitiesRepository;
        this.entitySetBuilderFactory = entitySetBuilderFactory;
        this.entitySaverFactory = entitySaverFactory;
        this.importProgressTracker = importProgressTracker;
    }

    // Пул потоков для асинхронной обработки файлов
    @Qualifier("fileProcessingExecutor")
    private final TaskExecutor fileProcessingExecutor;

    // Кэш стратегий обработки файлов
    private final List<FileProcessingStrategy> processingStrategies;

    // Карта активных задач импорта
    private final Map<Long, CompletableFuture<FileOperationDto>> activeImportTasks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<FileOperationDto> importFileAsync(
            MultipartFile file,
            Client client,
            Long mappingId,
            Long strategyId,
            Map<String, String> params) {

        log.info("Начало асинхронного импорта файла: {}, клиент: {}", file.getOriginalFilename(), client.getName());

        try {
            // Сохраняем файл во временную директорию
            Path tempFilePath = pathResolver.saveToTempFile(file, "import_" + client.getId());
            log.debug("Файл сохранен во временную директорию: {}", tempFilePath);

            // Создаем запись об операции в БД
            FileOperation operation = createFileOperation(client, file, tempFilePath);

            // Запускаем асинхронную обработку файла
            CompletableFuture<FileOperationDto> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Обрабатываем файл и получаем результат
                    return processUploadedFile(tempFilePath, client, mappingId, strategyId, params);
                } catch (Exception e) {
                    log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
                    operation.markAsFailed(e.getMessage());
                    fileOperationRepository.save(operation);
                    throw new RuntimeException("Ошибка при обработке файла: " + e.getMessage(), e);
                }
            }, fileProcessingExecutor);

            // Сохраняем задачу в карте активных задач
            activeImportTasks.put(operation.getId(), future);

            // Настраиваем обработку завершения задачи
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Задача импорта завершилась с ошибкой: {}", ex.getMessage());
                } else {
                    log.info("Задача импорта успешно завершена: операция #{}", operation.getId());
                }
                // Удаляем задачу из карты активных задач
                activeImportTasks.remove(operation.getId());
            });

            return future;
        } catch (IOException e) {
            log.error("Ошибка при сохранении файла: {}", e.getMessage(), e);
            throw new FileOperationException("Не удалось сохранить файл: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedEntityTypes() {
        List<String> types = new ArrayList<>();
        types.add("product");
        types.add("regiondata");
        types.add("competitordata");
        types.add("product_with_related");
        return types;
    }

    @Override
    public FileOperationDto getImportStatus(Long operationId) {
        log.debug("Запрос статуса импорта: операция #{}", operationId);

        // Получаем операцию из БД
        Optional<FileOperation> operationOpt = fileOperationRepository.findById(operationId);
        if (operationOpt.isEmpty()) {
            log.warn("Операция с ID {} не найдена", operationId);
            throw new FileOperationException("Операция с ID " + operationId + " не найдена");
        }

        FileOperation operation = operationOpt.get();

        // Проверяем статус операции
        if (operation.getStatus() == FileOperation.OperationStatus.COMPLETED ||
                operation.getStatus() == FileOperation.OperationStatus.FAILED) {
            // Операция уже завершена, возвращаем текущий статус
            return mapToDto(operation);
        }

        // Проверяем, есть ли операция в списке активных задач
        CompletableFuture<FileOperationDto> future = activeImportTasks.get(operationId);
        if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
            try {
                // Задача завершена, возвращаем результат
                return future.get();
            } catch (Exception e) {
                log.error("Ошибка при получении результата задачи: {}", e.getMessage(), e);
            }
        }

        // Возвращаем текущий статус из БД
        return mapToDto(operation);
    }

    @Override
    public Map<String, Object> analyzeFile(MultipartFile file) {
        log.info("Анализ файла: {}", file.getOriginalFilename());

        try {
            // Сохраняем файл во временную директорию
            Path tempFilePath = pathResolver.saveToTempFile(file, "analyze");

            // Получаем подходящий процессор для файла
            Optional<FileProcessor> processorOpt = fileProcessorFactory.createProcessor(tempFilePath);
            if (processorOpt.isEmpty()) {
                throw new FileOperationException("Не найден подходящий процессор для файла: " + file.getOriginalFilename());
            }

            FileProcessor processor = processorOpt.get();

            // Анализируем файл
            Map<String, Object> result = processor.analyzeFile(tempFilePath, null);

            // Добавляем информацию о процессоре
            result.put("processorType", processor.getClass().getSimpleName());

            // Удаляем временный файл
            pathResolver.deleteFile(tempFilePath);

            return result;
        } catch (IOException e) {
            log.error("Ошибка при анализе файла: {}", e.getMessage(), e);
            throw new FileOperationException("Не удалось проанализировать файл: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getAvailableMappings(Long clientId, String entityType) {
        log.debug("Получение доступных маппингов для клиента: {}, тип сущности: {}", clientId, entityType);
        return fieldMappingService.getAvailableMappingsForClient(clientId, entityType);
    }

    @Override
    public List<Map<String, Object>> getAvailableStrategies(String fileType) {
        log.debug("Получение доступных стратегий для типа файла: {}", fileType);

        List<Map<String, Object>> strategies = new ArrayList<>();

        for (FileProcessingStrategy strategy : processingStrategies) {
            if (strategy.getSupportedFileTypes().contains(fileType)) {
                Map<String, Object> strategyInfo = new HashMap<>();
                strategyInfo.put("id", strategy.getStrategyId());
                strategyInfo.put("name", strategy.getDisplayName());
                strategyInfo.put("description", strategy.getDescription());
                strategyInfo.put("parameters", strategy.getConfigurableParameters());
                strategies.add(strategyInfo);
            }
        }

        return strategies;
    }

    @Override
    public boolean cancelImport(Long operationId) {
        log.info("Запрос на отмену импорта: операция #{}", operationId);

        // Проверяем, есть ли операция в списке активных задач
        CompletableFuture<FileOperationDto> future = activeImportTasks.get(operationId);
        if (future != null && !future.isDone()) {
            // Отменяем задачу
            boolean cancelled = future.cancel(true);

            if (cancelled) {
                // Обновляем статус операции в БД
                Optional<FileOperation> operationOpt = fileOperationRepository.findById(operationId);
                if (operationOpt.isPresent()) {
                    FileOperation operation = operationOpt.get();
                    operation.markAsFailed("Операция отменена пользователем");
                    fileOperationRepository.save(operation);
                }

                // Удаляем задачу из карты активных задач
                activeImportTasks.remove(operationId);

                log.info("Импорт успешно отменен: операция #{}", operationId);
                return true;
            }
        }

        log.warn("Не удалось отменить импорт: операция не найдена или уже завершена: #{}", operationId);
        return false;
    }

    /**
     * Запускает асинхронную обработку загруженного файла.
     * Важно: этот метод НЕ дожидается завершения обработки.
     *
     * @param filePath путь к файлу
     * @param client клиент
     * @param mappingId идентификатор маппинга полей
     * @param strategyId идентификатор стратегии обработки
     * @param params дополнительные параметры для обработки
     * @return DTO созданной операции (с начальным статусом)
     */
    @Override
    public FileOperationDto processUploadedFile(
            Path filePath,
            Client client,
            Long mappingId,
            Long strategyId,
            Map<String, String> params) {

        log.info("Запуск асинхронной обработки файла: {}, клиент: {}", filePath, client.getName());

        try {
            // Определяем тип сущности из параметров
            String entityType = getEntityTypeFromParams(params);
            log.debug("Тип сущности для импорта: {}", entityType);

            // Создаем запись об операции
            FileOperation operation = findOrCreateFileOperation(client, filePath, params);
            operation.markAsProcessing(); // Сразу меняем статус на "в процессе"
            operation = fileOperationRepository.save(operation);
            log.debug("Создана запись об операции: #{}, статус: {}", operation.getId(), operation.getStatus());

            // Инициализируем трекер прогресса
            long operationId = operation.getId();
            importProgressTracker.initProgress(operationId, 0);

            // Получаем маппинг полей
            Map<String, String> fieldMapping = getFieldMapping(mappingId, params);

            // Создаем и настраиваем задачу для асинхронного выполнения
            FileImportJob job = FileImportJob.builder()
                    .operation(operation)
                    .filePath(filePath)
                    .client(client)
                    .fieldMapping(fieldMapping)
                    .params(params)
                    .entityType(entityType)
                    .fileOperationRepository(fileOperationRepository)
                    .processorFactory(fileProcessorFactory)
                    .processingStrategies(processingStrategies)
                    .progressTracker(importProgressTracker)
                    .pathResolver(pathResolver)
                    .entitySaverFactory(entitySaverFactory)
                    .build();

            // Запускаем задачу асинхронно
            CompletableFuture<FileOperationDto> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return job.call();
                } catch (Exception e) {
                    log.error("Ошибка при выполнении задачи импорта: {}", e.getMessage(), e);
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new FileOperationException("Ошибка при обработке файла: " + e.getMessage(), e);
                }
            }, fileProcessingExecutor);

            // Сохраняем задачу и ее Future в карту активных задач
            activeImportTasks.put(operationId, future);

            // Добавляем обработчик завершения задачи для удаления из списка активных
            future.whenComplete((result, ex) -> {
                activeImportTasks.remove(operationId);
                if (ex != null) {
                    log.error("Задача импорта завершилась с ошибкой: {}", ex.getMessage(), ex);
                } else {
                    log.info("Задача импорта успешно завершена: операция #{}", operationId);
                }
            });

            // Возвращаем DTO с информацией об операции (она уже в статусе PROCESSING)
            return mapToDto(operation);

        } catch (Exception e) {
            log.error("Ошибка при подготовке задачи импорта: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при запуске задачи импорта: " + e.getMessage(), e);
        }
    }

    /**
     * Получает маппинг полей по ID или из параметров.
     *
     * @param mappingId ID маппинга
     * @param params параметры запроса
     * @return маппинг полей
     */
    private Map<String, String> getFieldMapping(Long mappingId, Map<String, String> params) {
        Map<String, String> fieldMapping = null;

        // Если mappingId задан, получаем маппинг по ID
        if (mappingId != null) {
            fieldMapping = fieldMappingService.getMappingById(mappingId);
        }

        // Проверяем, есть ли маппинг в параметрах
        if ((fieldMapping == null || fieldMapping.isEmpty()) && params != null) {
            fieldMapping = extractFieldMappingFromParams(params);
        }

        return fieldMapping != null ? fieldMapping : new HashMap<>();
    }

    /**
     * Извлекает маппинг полей из параметров запроса.
     * Параметры с префиксом "mapping[" содержат маппинг поля.
     */
    private Map<String, String> extractFieldMappingFromParams(Map<String, String> params) {
        Map<String, String> fieldMapping = new HashMap<>();

        if (params == null) {
            return fieldMapping;
        }

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("mapping[") && key.endsWith("]")) {
                // Извлекаем имя исходного поля из ключа (между "mapping[" и "]")
                String sourceField = key.substring(8, key.length() - 1);
                String targetField = entry.getValue();

                // Добавляем в маппинг только если целевое поле не пустое
                if (targetField != null && !targetField.trim().isEmpty()) {
                    fieldMapping.put(sourceField, targetField);
                }
            }
        }

        return fieldMapping;
    }

    @Override
    public ImportableEntity createEntityInstance(String entityType) {
        if (entityType == null) {
            return null;
        }

        switch (entityType.toLowerCase()) {
            case "product":
                Product product = new Product();
                product.setTransformerFactory(transformerFactory);
                return product;
            case "regiondata":
                RegionData regionData = new RegionData();
                regionData.setTransformerFactory(transformerFactory);
                return regionData;
            case "competitordata":
                CompetitorData competitorData = new CompetitorData();
                competitorData.setTransformerFactory(transformerFactory);
                return competitorData;
            default:
                log.warn("Неизвестный тип сущности: {}", entityType);
                return null;
        }
    }

    /**
     * Создает запись об операции импорта файла.
     *
     * @param client   клиент
     * @param file     загруженный файл
     * @param filePath путь к сохраненному файлу
     * @return созданная операция
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

        // Сохраняем операцию в БД
        return fileOperationRepository.save(operation);
    }

    /**
     * Ищет существующую операцию или создает новую.
     *
     * @param client   клиент
     * @param filePath путь к файлу
     * @return операция
     */
    private FileOperation findOrCreateFileOperation(Client client, Path filePath, Map<String, String> params) {
        // Проверяем, есть ли уже операция для этого файла
        List<FileOperation> operations = fileOperationRepository.findByClientIdAndStatus(
                client.getId(), FileOperation.OperationStatus.PENDING);

        for (FileOperation operation : operations) {
            if (filePath.toString().equals(operation.getSourceFilePath())) {
                return operation;
            }
        }

        // Создаем новую операцию
        FileOperation operation = new FileOperation();
        operation.setClient(client);
        operation.setOperationType(FileOperation.OperationType.IMPORT);
        operation.setFileName(filePath.getFileName().toString());
        operation.setFileType(getFileTypeFromPath(filePath));
        operation.setStatus(FileOperation.OperationStatus.PENDING);
        operation.setSourceFilePath(filePath.toString());
        operation.setFileSize(pathResolver.getFileSize(filePath));
        operation.setStartedAt(ZonedDateTime.now());

        // Установка параметров операции из переданных параметров
        if (params != null) {
            // Установка размера пакета
            if (params.containsKey("batchSize")) {
                try {
                    int batchSize = Integer.parseInt(params.get("batchSize"));
                    operation.setBatchSize(batchSize);
                    log.debug("Установлен размер пакета: {}", batchSize);
                } catch (NumberFormatException e) {
                    log.warn("Невозможно преобразовать batchSize к int: {}", params.get("batchSize"));
                }
            }

            // Установка стратегии обработки
            if (params.containsKey("processingStrategy")) {
                operation.setProcessingStrategy(params.get("processingStrategy"));
                log.debug("Установлена стратегия обработки: {}", params.get("processingStrategy"));
            }

            // Установка способа обработки ошибок
            if (params.containsKey("errorHandling")) {
                operation.setErrorHandling(params.get("errorHandling"));
                log.debug("Установлен способ обработки ошибок: {}", params.get("errorHandling"));
            }

            // Установка способа обработки дубликатов
            if (params.containsKey("duplicateHandling")) {
                operation.setDuplicateHandling(params.get("duplicateHandling"));
                log.debug("Установлен способ обработки дубликатов: {}", params.get("duplicateHandling"));
            }

            // Сохранение всех параметров в виде JSON в processingParams
            try {
                ObjectMapper mapper = new ObjectMapper();
                operation.setProcessingParams(mapper.writeValueAsString(params));
            } catch (Exception e) {
                log.error("Ошибка при сериализации параметров: {}", e.getMessage());
            }
        }

        // Сохраняем операцию в БД
        return fileOperationRepository.save(operation);
    }


    /**
     * Определяет тип файла по MultipartFile.
     *
     * @param file загруженный файл
     * @return тип файла
     */
    private String getFileType(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex > 0) {
                return originalFilename.substring(lastDotIndex + 1).toUpperCase();
            }
        }

        // Попытка определить по contentType
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.contains("csv")) {
                return "CSV";
            } else if (contentType.contains("excel") || contentType.contains("spreadsheet")) {
                return "EXCEL";
            }
        }

        return "UNKNOWN";
    }

    /**
     * Определяет тип файла по пути.
     *
     * @param filePath путь к файлу
     * @return тип файла
     */
    private String getFileTypeFromPath(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1).toUpperCase();
        }
        return "UNKNOWN";
    }

    /**
     * Получает тип сущности из параметров.
     * Если тип не указан, возвращает значение по умолчанию.
     *
     * @param params параметры
     * @return тип сущности
     */
    private String getEntityTypeFromParams(Map<String, String> params) {
        if (params != null && params.containsKey("entityType")) {
            return params.get("entityType");
        }
        return "product"; // По умолчанию - продукт
    }

    /**
     * Сохраняет сущности в БД.
     *
     * @param entities   список сущностей
     * @param entityType тип сущности
     * @return количество сохраненных сущностей
     */
    @Transactional
    public int saveEntities(List<ImportableEntity> entities, String entityType) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        // Проверяем специальные типы сущностей для использования единого репозитория
        if (entityType != null && entitySetBuilderFactory.supportsEntityType(entityType)) {
            log.info("Используем единый репозиторий для сохранения связанных сущностей типа: {}", entityType);
            return relatedEntitiesRepository.saveRelatedEntities(entities);
        }

        // Стандартная обработка для обычных сущностей
        int savedCount = 0;
        switch (entityType.toLowerCase()) {
            case "product":
                List<Product> products = entities.stream()
                        .filter(e -> e instanceof Product)
                        .map(e -> (Product) e)
                        .collect(Collectors.toList());
                savedCount = productService.saveProducts(products);
                log.info("Сохранено {} продуктов", savedCount);
                break;

            case "regiondata":
                List<RegionData> regionDataList = entities.stream()
                        .filter(e -> e instanceof RegionData)
                        .map(e -> (RegionData) e)
                        .collect(Collectors.toList());
                savedCount = regionDataService.saveRegionDataList(regionDataList);
                log.info("Сохранено {} данных регионов", savedCount);
                break;

            case "competitordata":
                List<CompetitorData> competitorDataList = entities.stream()
                        .filter(e -> e instanceof CompetitorData)
                        .map(e -> (CompetitorData) e)
                        .collect(Collectors.toList());
                savedCount = competitorDataService.saveCompetitorDataList(competitorDataList);
                log.info("Сохранено {} данных конкурентов", savedCount);
                break;

            default:
                log.warn("Неизвестный тип сущности для сохранения: {}", entityType);
        }

        return savedCount;
    }

    /**
     * Преобразует модель операции в DTO.
     *
     * @param operation операция
     * @return DTO операции
     */
    private FileOperationDto mapToDto(FileOperation operation) {
        if (operation == null) {
            return null;
        }

        return FileOperationDto.builder()
                .id(operation.getId())
                .clientId(operation.getClient() != null ? operation.getClient().getId() : null)
                .clientName(operation.getClient() != null ? operation.getClient().getName() : null)
                .operationType(operation.getOperationType())
                .fileName(operation.getFileName())
                .fileType(operation.getFileType())
                .recordCount(operation.getRecordCount())
                .status(operation.getStatus())
                .errorMessage(operation.getErrorMessage())
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .createdBy(operation.getCreatedBy())
                // Добавляем поля, которые не были в DTO, но необходимы для отображения прогресса
                .processingProgress(operation.getProcessingProgress())
                .processedRecords(operation.getProcessedRecords())
                .totalRecords(operation.getTotalRecords())
                .batchSize(operation.getBatchSize())
                .processingStrategy(operation.getProcessingStrategy())
                .errorHandling(operation.getErrorHandling())
                .duplicateHandling(operation.getDuplicateHandling())
                .build();
    }
}