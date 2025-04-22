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
 * Обеспечивает функциональность загрузки, обработки и сохранения данных из файлов.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileImportServiceImpl implements FileImportService {

    private final PathResolver pathResolver;
    private final FileOperationRepository fileOperationRepository;
    private final FileProcessorFactory fileProcessorFactory;
    private final FieldMappingService fieldMappingService;
    private final ValueTransformerFactory transformerFactory;
    private final ProductService productService;
    private final RegionService regionService;
    private final CompetitorService competitorService;
    @Autowired
    private CompositeEntityService compositeEntityService;

    // Пул потоков для асинхронной обработки файлов
    @Qualifier("fileProcessingExecutor")
    private final TaskExecutor fileProcessingExecutor;

    // Кэш стратегий обработки файлов
    private final List<FileProcessingStrategy> processingStrategies;

    // Карта активных задач импорта
    private final Map<Long, CompletableFuture<FileOperationDto>> activeImportTasks = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> analyzeFileWithOptions(MultipartFile file, FileReadingOptions options) {
        log.info("Анализ файла с FileReadingOptions: {}", file.getOriginalFilename());

        try {
            // Сохраняем файл во временную директорию
            Path tempFilePath = pathResolver.saveToTempFile(file, "analyze");

            // Получаем подходящий процессор для файла
            Optional<FileProcessor> processorOpt = fileProcessorFactory.createProcessor(tempFilePath);
            if (processorOpt.isEmpty()) {
                throw new FileOperationException("Не найден подходящий процессор для файла: " + file.getOriginalFilename());
            }

            FileProcessor processor = processorOpt.get();

            // Анализируем файл с использованием FileReadingOptions
            // Пока вызываем существующий метод с преобразованием в Map для обратной совместимости
            Map<String, Object> result = processor.analyzeFileWithOptions(tempFilePath, options);

            // Добавляем информацию о процессоре
            result.put("processorType", processor.getClass().getSimpleName());

            // Добавляем параметры
            result.put("options", options);

            // Удаляем временный файл
            pathResolver.deleteFile(tempFilePath);

            return result;
        } catch (IOException e) {
            log.error("Ошибка при анализе файла: {}", e.getMessage(), e);
            throw new FileOperationException("Не удалось проанализировать файл: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<FileOperationDto> importFileAsyncWithOptions(
            MultipartFile file,
            Client client,
            Long mappingId,
            Long strategyId,
            FileReadingOptions options) {

        log.info("Начало асинхронного импорта файла с FileReadingOptions: {}, клиент: {}, составная сущность: {}",
                file.getOriginalFilename(), client.getName(),
                Boolean.parseBoolean(options.getAdditionalParam("composite", "false")));

        try {
            // Сохраняем файл во временную директорию
            Path tempFilePath = pathResolver.saveToTempFile(file, "import_" + client.getId());
            log.debug("Файл сохранен во временную директорию: {}", tempFilePath);

            // Создаем запись об операции в БД
            FileOperation operation = createFileOperation(client, file, tempFilePath);

            // Запускаем асинхронную обработку файла
            CompletableFuture<FileOperationDto> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Обрабатываем файл с использованием FileReadingOptions
                    return processUploadedFileWithOptions(tempFilePath, client, mappingId, strategyId, options);
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
    @Transactional
    public FileOperationDto processUploadedFileWithOptions(
            Path filePath,
            Client client,
            Long mappingId,
            Long strategyId,
            FileReadingOptions options) {

        log.info("Обработка загруженного файла с FileReadingOptions: {}, клиент: {}", filePath, client.getName());

        // Создаем запись об операции, если еще не создана
        FileOperation operation = findOrCreateFileOperation(client, filePath);

        try {
            // Валидация и логирование параметров
            options.validate().logSettings(log);

            // Получаем подходящий процессор для файла
            Optional<FileProcessor> processorOpt = fileProcessorFactory.createProcessor(filePath);
            if (processorOpt.isEmpty()) {
                throw new FileOperationException("Не найден подходящий процессор для файла: " + filePath);
            }

            FileProcessor processor = processorOpt.get();
            log.debug("Выбран процессор: {}", processor.getClass().getSimpleName());

            // Определяем тип сущности для импорта из дополнительных параметров
            String entityType = options.getAdditionalParam("entityType", "product");
            log.debug("Тип сущности для импорта: {}", entityType);

            // Проверяем, является ли импорт составным
            boolean isComposite = Boolean.parseBoolean(options.getAdditionalParam("composite", "false"));
            log.debug("Составной импорт: {}", isComposite);

            // Обновляем стратегию обработки на основе типа сущности и клиента
            options.updateStrategy(entityType, client.getId());

            // Получаем маппинг полей
            Map<String, String> fieldMapping = mappingId != null
                    ? fieldMappingService.getMappingById(mappingId)
                    : null;

            // Если маппинг не указан, можем попробовать автоматически сопоставить поля
            if (fieldMapping == null || fieldMapping.isEmpty()) {
                Map<String, Object> fileAnalysis = processor.analyzeFileWithOptions(filePath, options);
                if (fileAnalysis.containsKey("headers")) {
                    @SuppressWarnings("unchecked")
                    List<String> headers = (List<String>) fileAnalysis.get("headers");
                    fieldMapping = fieldMappingService.suggestMapping(headers, entityType);
                    log.debug("Автоматически создан маппинг полей: {}", fieldMapping);
                } else {
                    throw new FileOperationException("Не удалось определить заголовки файла");
                }
            }

            // Меняем статус операции на "в процессе"
            operation.markAsProcessing();
            operation = fileOperationRepository.save(operation);

            // Добавляем информацию об операции в параметры
            options.getAdditionalParams().put("operationId", operation.getId().toString());
            options.getAdditionalParams().put("totalRecords", String.valueOf(operation.getTotalRecords()));

            List<ImportableEntity> entities;

            // В зависимости от типа импорта, используем соответствующую логику
            if (isComposite) {
                // Логика для составных сущностей с использованием FileReadingOptions
                entities = processCompositeEntitiesWithOptions(processor, filePath, entityType, client, fieldMapping, options, operation);
            } else {
                // Стандартная логика для одиночных сущностей
                entities = processor.processFileWithOptions(filePath, entityType, client, fieldMapping, options, operation);
            }

            // Сохраняем сущности в БД
            int savedEntities = saveEntities(entities, entityType);

            // Обновляем статус операции
            operation.markAsCompleted(savedEntities);
            operation = fileOperationRepository.save(operation);

            log.info("Файл успешно обработан: {}, создано сущностей: {}", filePath, savedEntities);

            // Перемещаем файл из временной директории в постоянную
            Path permanentPath = pathResolver.moveFromTempToUpload(filePath, "imported_" + client.getId());
            operation.setResultFilePath(permanentPath.toString());
            operation = fileOperationRepository.save(operation);

            return mapToDto(operation);
        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            operation.markAsFailed(e.getMessage());
            operation = fileOperationRepository.save(operation);
            throw new FileOperationException("Ошибка при обработке файла: " + e.getMessage(), e);
        }
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
     * Обрабатывает файл для составных сущностей.
     *
     * @param processor      процессор файлов
     * @param filePath       путь к файлу
     * @param mainEntityType основной тип сущности
     * @param client         клиент
     * @param fieldMapping   маппинг полей
     * @param options        параметры обработки
     * @param operation      информация об операции
     * @return список созданных сущностей
     */
    private List<ImportableEntity> processCompositeEntitiesWithOptions(
            FileProcessor processor,
            Path filePath,
            String mainEntityType,
            Client client,
            Map<String, String> fieldMapping,
            FileReadingOptions options,
            FileOperation operation) {

        log.info("Обработка составных сущностей с FileReadingOptions. Основной тип: {}", mainEntityType);

        // Получаем связанные сущности
        List<String> relatedEntities = getRelatedEntitiesFromOptions(options);
        log.debug("Связанные сущности: {}", relatedEntities);

        // Используем процессор для получения сырых данных из файла
        List<Map<String, String>> rawData = processor.readRawDataWithOptions(filePath, options);
        log.debug("Прочитано {} строк данных из файла", rawData.size());

        // Обновляем информацию о количестве записей
        if (operation != null) {
            operation.setTotalRecords(rawData.size());
        }

        // Результирующий список сущностей
        List<ImportableEntity> resultEntities = new ArrayList<>();
        int processedRecords = 0;

        // Обрабатываем каждую строку данных
        for (Map<String, String> row : rawData) {
            processedRecords++;

            try {
                // Применяем маппинг полей перед обработкой
                Map<String, String> mappedData = applyFieldMapping(row, fieldMapping);

                // Обрабатываем строку данных и получаем список сущностей с FileReadingOptions
                List<ImportableEntity> entitiesFromRow = compositeEntityService.processRowWithOptions(
                        mappedData, fieldMapping, client.getId(), options);

                // Добавляем созданные сущности в результат
                resultEntities.addAll(entitiesFromRow);

            } catch (Exception e) {
                // Обработка ошибок в зависимости от настроек
                String errorHandling = options.getErrorHandling();
                if ("stop".equals(errorHandling)) {
                    throw new FileOperationException("Ошибка при обработке строки " + processedRecords + ": " + e.getMessage());
                } else {
                    log.warn("Ошибка при обработке строки {}: {}", processedRecords, e.getMessage());
                }
            }
        }

        // Очищаем кэш после завершения импорта
        compositeEntityService.clearCache();

        log.info("Обработка составных сущностей завершена. Создано сущностей: {}", resultEntities.size());
        return resultEntities;
    }

    /**
     * Получает список связанных сущностей из параметров FileReadingOptions.
     *
     * @param options параметры обработки
     * @return список связанных сущностей
     */
    private List<String> getRelatedEntitiesFromOptions(FileReadingOptions options) {
        if (options == null) {
            return Collections.emptyList();
        }

        String relatedEntitiesStr = options.getAdditionalParam("relatedEntities", "");
        if (relatedEntitiesStr.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.asList(relatedEntitiesStr.split(","));
    }

    /**
     * Применяет маппинг полей к данным строки.
     *
     * @param rawData      исходные данные
     * @param fieldMapping маппинг полей
     * @return преобразованные данные
     */
    private Map<String, String> applyFieldMapping(Map<String, String> rawData, Map<String, String> fieldMapping) {
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            return new HashMap<>(rawData);
        }

        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
            String fileHeader = entry.getKey();
            String entityField = entry.getValue();

            if (rawData.containsKey(fileHeader)) {
                result.put(entityField, rawData.get(fileHeader));
            }
        }

        return result;
    }


    /**
     * Проверяет, является ли импорт составным.
     *
     * @param params параметры импорта
     * @return true, если импорт составной
     */
    private boolean isCompositeImport(Map<String, String> params) {
        if (params == null) {
            return false;
        }
        return Boolean.parseBoolean(params.getOrDefault("composite", "false"));
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
                Region region = new Region();
                region.setTransformerFactory(transformerFactory);
                return region;
            case "competitordata":
                Competitor competitor = new Competitor();
                competitor.setTransformerFactory(transformerFactory);
                return competitor;
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
    private FileOperation findOrCreateFileOperation(Client client, Path filePath) {
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

        int savedCount = 0;

        // В зависимости от типа сущности, используем соответствующий сервис
        switch (entityType.toLowerCase()) {
            case "product":
                List<Product> products = entities.stream()
                        .filter(e -> e instanceof Product)
                        .map(e -> (Product) e)
                        .toList();
                savedCount = productService.saveProducts(products);
                log.info("Сохранено {} продуктов", savedCount);
                break;

            case "region":
                List<Region> regionList = entities.stream()
                        .filter(e -> e instanceof Region)
                        .map(e -> (Region) e)
                        .toList();
                savedCount = regionService.saveRegionList(regionList);
                log.info("Сохранено {} данных регионов", savedCount);
                break;

            case "competitor":
                List<Competitor> competitorList = entities.stream()
                        .filter(e -> e instanceof Competitor)
                        .map(e -> (Competitor) e)
                        .toList();
                savedCount = competitorService.saveCompetitorList(competitorList);
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
// TODO Реализовать сохранение названия клиента
        return FileOperationDto.builder()
                .id(operation.getId())
                .clientId(operation.getClient() != null ? operation.getClient().getId() : null)
//                .clientName(operation.getClient() != null ? operation.getClient().getName() : null)
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
}