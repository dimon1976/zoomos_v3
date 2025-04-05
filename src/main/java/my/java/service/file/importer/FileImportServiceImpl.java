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
import my.java.service.file.mapping.FieldMappingService;
import my.java.service.file.processor.FileProcessor;
import my.java.service.file.processor.FileProcessorFactory;
import my.java.service.file.strategy.FileProcessingStrategy;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.service.entity.product.ProductService;
import my.java.service.entity.region.RegionService;
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

    @Override
    @Transactional
    public FileOperationDto processUploadedFile(
            Path filePath,
            Client client,
            Long mappingId,
            Long strategyId,
            Map<String, String> params) {

        log.info("Обработка загруженного файла: {}, клиент: {}", filePath, client.getName());

        // Создаем запись об операции, если еще не создана
        FileOperation operation = findOrCreateFileOperation(client, filePath);

        try {
            // Получаем подходящий процессор для файла
            Optional<FileProcessor> processorOpt = fileProcessorFactory.createProcessor(filePath);
            if (processorOpt.isEmpty()) {
                throw new FileOperationException("Не найден подходящий процессор для файла: " + filePath);
            }

            FileProcessor processor = processorOpt.get();
            log.debug("Выбран процессор: {}", processor.getClass().getSimpleName());

            // Определяем тип сущности для импорта (можно получить из параметров)
            String entityType = getEntityTypeFromParams(params);
            log.debug("Тип сущности для импорта: {}", entityType);

            // Получаем маппинг полей
            Map<String, String> fieldMapping = mappingId != null
                    ? fieldMappingService.getMappingById(mappingId)
                    : null;

            // Если маппинг не указан, можем попробовать автоматически сопоставить поля
            if (fieldMapping == null || fieldMapping.isEmpty()) {
                Map<String, Object> fileAnalysis = processor.analyzeFile(filePath, null);
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

            // Обрабатываем файл и получаем список сущностей
            List<ImportableEntity> entities = processor.processFile(
                    filePath, entityType, client, fieldMapping, params, operation);

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