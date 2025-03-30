package my.java.service.file.exporter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.repository.CompetitorDataRepository;
import my.java.repository.FileOperationRepository;
import my.java.repository.ProductRepository;
import my.java.repository.RegionDataRepository;
import my.java.service.file.exporter.ExportConfig;
import my.java.service.file.exporter.FileExportService;
import my.java.service.file.exporter.FileFormat;
import my.java.service.file.exporter.processor.FileExportProcessor;
import my.java.service.file.exporter.processor.FileExportProcessorFactory;
import my.java.service.file.exporter.processor.composite.ProductWithRelatedEntitiesExporter;
import my.java.service.file.exporter.processor.composite.ProductWithRelatedEntitiesExporter.CompositeProductEntity;
import my.java.service.file.exporter.tracker.ExportProgressTracker;
import my.java.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Реализация сервиса экспорта файлов
 * Путь: /java/my/java/service/file/exporter/impl/FileExportServiceImpl.java
 */
@Service
@Slf4j
public class FileExportServiceImpl implements FileExportService {

    private final Map<Class<? extends ImportableEntity>, JpaRepository<? extends ImportableEntity, ?>> repositories = new HashMap<>();
    private final FileOperationRepository fileOperationRepository;
    private final ProductWithRelatedEntitiesExporter compositeExporter;
    private final ExportProgressTracker progressTracker;
    private final FileExportProcessorFactory processorFactory;
    private final PathResolver pathResolver;
    private final ObjectMapper objectMapper;

    // Добавляем репозитории
    private final ProductRepository productRepository;
    private final RegionDataRepository regionDataRepository;
    private final CompetitorDataRepository competitorDataRepository;

    // Обновляем конструктор, добавляя новые поля
    @Autowired
    public FileExportServiceImpl(
            FileOperationRepository fileOperationRepository,
            ExportProgressTracker progressTracker,
            FileExportProcessorFactory processorFactory,
            PathResolver pathResolver,
            ObjectMapper objectMapper,
            ProductRepository productRepository,
            RegionDataRepository regionDataRepository,
            CompetitorDataRepository competitorDataRepository) {
        this.fileOperationRepository = fileOperationRepository;
        this.progressTracker = progressTracker;
        this.processorFactory = processorFactory;
        this.pathResolver = pathResolver;
        this.objectMapper = objectMapper;
        this.productRepository = productRepository;
        this.regionDataRepository = regionDataRepository;
        this.competitorDataRepository = competitorDataRepository;

        this.compositeExporter = new ProductWithRelatedEntitiesExporter(
                regionDataRepository, competitorDataRepository);
        // Инициализируем карту репозиториев
        initializeRepositoriesMap();
    }

    /**
     * Инициализирует карту репозиториев.
     * Вызывается после внедрения всех зависимостей через конструктор.
     */
    private void initializeRepositoriesMap() {
        repositories.put(Product.class, productRepository);
        repositories.put(RegionData.class, regionDataRepository);
        repositories.put(Competitor.class, competitorDataRepository);

        log.info("Инициализирована карта репозиториев: {} репозиториев", repositories.size());
        // Выводим отладочную информацию для проверки
        for (Class<?> cls : repositories.keySet()) {
            log.debug("Зарегистрирован репозиторий для класса: {}", cls.getName());
        }
    }

    @Override
    @Transactional
    public <T extends ImportableEntity> FileOperationDto exportData(
            Class<T> entityClass,
            Client client,
            Map<String, Object> exportParams,
            OutputStream outputStream,
            String fileFormat,
            String entityType) {

        log.info("Начат экспорт данных типа {} для клиента {}, формат: {}",
                entityClass.getSimpleName(), client.getName(), fileFormat);

        try {
            // Создаем запись в БД о новой операции экспорта
            FileOperation operation = createExportOperation(client, fileFormat, entityType);

            // Создаем копию параметров для фильтрации (без параметров экспорта)
            Map<String, Object> filterCriteria = new HashMap<>(exportParams);

            // Извлекаем список полей для включения, если он есть
            List<String> includedFields = null;
            if (exportParams.containsKey("fields")) {
                Object fieldsObj = exportParams.get("fields");
                filterCriteria.remove("fields"); // Удаляем из фильтров

                if (fieldsObj instanceof List) {
                    // Если передан список полей
                    includedFields = (List<String>) fieldsObj;
                    log.debug("Экспорт будет включать выбранные поля из списка: {}", includedFields);
                } else if (fieldsObj instanceof String) {
                    // Если передана строка с запятыми
                    String fieldsStr = (String) fieldsObj;
                    if (!fieldsStr.isEmpty()) {
                        includedFields = Arrays.asList(fieldsStr.split("\\s*,\\s*"));
                        log.debug("Экспорт будет включать выбранные поля из строки: {}", includedFields);
                    }
                } else {
                    log.warn("Параметр fields имеет неожиданный тип: {}", fieldsObj.getClass().getName());
                }
            }

            // Сохраняем критерии фильтрации в БД (без параметров экспорта)
            if (!filterCriteria.isEmpty()) {
                String filterJson = objectMapper.writeValueAsString(filterCriteria);
                operation.setExportFilterCriteria(filterJson);
                operation = fileOperationRepository.save(operation);
            }

            // Инициализируем трекер прогресса
            progressTracker.initializeOperation(operation.getId(),
                    "Экспорт данных " + entityClass.getSimpleName());

            // Получаем репозиторий для класса сущности
            JpaRepository<T, ?> repository = getRepositoryForEntityClass(entityClass);

            // Загружаем данные из БД с применением фильтров
            List<T> entities = loadEntitiesWithFilter(repository, filterCriteria);

            // Обновляем трекер прогресса
            progressTracker.updateTotal(operation.getId(), entities.size());
            progressTracker.updateStatus(operation.getId(), "Найдено записей: " + entities.size());

            // Обновляем информацию в БД
            operation.setTotalRecords(entities.size());
            operation.markAsProcessing();
            operation = fileOperationRepository.save(operation);

            // Создаем конфигурацию экспорта с учетом выбранных полей
            ExportConfig config;
            if (includedFields != null && !includedFields.isEmpty()) {
                // Используем Builder для создания конфигурации с выбранными полями
                ExportConfig.ExportConfigBuilder configBuilder = ExportConfig.builder()
                        .includeHeader(true)
                        .applyFormatting(true)
                        .batchSize(1000)
                        .asyncProcessing(false);

                // Добавляем выбранные поля
                configBuilder.includedFields(new ArrayList<>(includedFields));

                // Создаем конфигурацию
                config = configBuilder.build();
                log.debug("Создана конфигурация экспорта с выбранными полями: {}", includedFields);
            } else {
                // Если поля не выбраны, используем конфигурацию по умолчанию
                config = ExportConfig.createDefault();
                log.debug("Используется конфигурация экспорта по умолчанию (все поля)");
            }

            // Получаем процессор для формата файла
            FileFormat format = FileFormat.fromString(fileFormat);
            FileExportProcessor<T> processor = processorFactory.createProcessor(format);

            // Выполняем экспорт
            processor.process(entities, config, outputStream, progressTracker, operation.getId());

            // Обновляем статус операции в БД
            operation.markAsCompleted(entities.size());
            operation = fileOperationRepository.save(operation);

            return mapToDto(operation);

        } catch (Exception e) {
            log.error("Ошибка при экспорте данных: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при экспорте данных: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public FileOperationDto exportDataByImportOperation(
            Long importOperationId,
            OutputStream outputStream,
            String fileFormat) {

        log.info("Начат экспорт данных на основе импорта с ID: {}, формат: {}",
                importOperationId, fileFormat);

        try {
            // Получаем информацию об операции импорта
            FileOperation importOperation = fileOperationRepository.findById(importOperationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция импорта не найдена: " + importOperationId));

            // Проверяем, что это действительно операция импорта
            if (importOperation.getOperationType() != FileOperation.OperationType.IMPORT) {
                throw new IllegalArgumentException("Операция с ID " + importOperationId + " не является импортом");
            }

            // Создаем новую операцию экспорта
            Client client = importOperation.getClient();
            String entityType = importOperation.getEntityType();
            FileOperation exportOperation = createExportOperation(client, fileFormat, entityType);

            // Устанавливаем связь с операцией импорта
            String description = "Экспорт данных импортированных из файла " + importOperation.getFileName();
            exportOperation.setOperationDescription(description);
            exportOperation = fileOperationRepository.save(exportOperation);

            // Инициализируем трекер прогресса
            progressTracker.initializeOperation(exportOperation.getId(), description);

            // Создаем критерии фильтрации на основе операции импорта
            // Это позволит выбрать только те записи, которые были созданы этой операцией импорта
            Map<String, Object> filterCriteria = new HashMap<>();
            filterCriteria.put("fileOperationId", importOperationId);

            // Находим класс сущности
            Class<? extends ImportableEntity> entityClass = determineEntityClass(entityType);

            // Получаем репозиторий для класса сущности
            JpaRepository<? extends ImportableEntity, ?> repository = getRepositoryForEntityClass(entityClass);

            // Загружаем данные из БД
            List<? extends ImportableEntity> entities = loadEntitiesWithFilter(repository, filterCriteria);

            // Обновляем трекер прогресса
            progressTracker.updateTotal(exportOperation.getId(), entities.size());
            progressTracker.updateStatus(exportOperation.getId(), "Найдено записей: " + entities.size());

            // Обновляем информацию в БД
            exportOperation.setTotalRecords(entities.size());
            exportOperation.markAsProcessing();
            exportOperation = fileOperationRepository.save(exportOperation);

            // Создаем конфигурацию экспорта
            // Примечание: для экспорта из импорта обычно нужны все поля,
            // поэтому специальной конфигурации полей не задаем
            ExportConfig config = ExportConfig.createDefault();

            // Получаем процессор для формата файла
            FileFormat format = FileFormat.fromString(fileFormat);
            FileExportProcessor processor = processorFactory.createProcessor(format);

            // Выполняем экспорт
            processor.process(entities, config, outputStream, progressTracker, exportOperation.getId());

            // Обновляем статус операции в БД
            exportOperation.markAsCompleted(entities.size());
            exportOperation = fileOperationRepository.save(exportOperation);

            return mapToDto(exportOperation);

        } catch (Exception e) {
            log.error("Ошибка при экспорте данных импорта: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при экспорте данных импорта: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public <T extends ImportableEntity> FileOperationDto exportEntities(
            List<T> entities,
            Client client,
            OutputStream outputStream,
            String fileFormat,
            String entityType) {

        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("Пустой список сущностей для экспорта");
        }

        log.info("Начат экспорт {} записей типа {} для клиента {}, формат: {}",
                entities.size(), entityType, client.getName(), fileFormat);

        try {
            // Создаем запись в БД о новой операции экспорта
            FileOperation operation = createExportOperation(client, fileFormat, entityType);

            // Инициализируем трекер прогресса
            progressTracker.initializeOperation(operation.getId(),
                    "Экспорт " + entities.size() + " записей типа " + entityType);

            // Обновляем трекер прогресса и информацию в БД
            progressTracker.updateTotal(operation.getId(), entities.size());
            operation.setTotalRecords(entities.size());
            operation.markAsProcessing();
            operation = fileOperationRepository.save(operation);

            // Создаем конфигурацию экспорта по умолчанию
            // Примечание: здесь мы можем добавить извлечение полей из параметров запроса,
            // если это необходимо для выбранных сущностей
            ExportConfig config = ExportConfig.createDefault();

            // Получаем процессор для формата файла
            FileFormat format = FileFormat.fromString(fileFormat);
            FileExportProcessor<T> processor = processorFactory.createProcessor(format);

            // Выполняем экспорт
            processor.process(entities, config, outputStream, progressTracker, operation.getId());

            // Обновляем статус операции в БД
            operation.markAsCompleted(entities.size());
            operation = fileOperationRepository.save(operation);

            return mapToDto(operation);

        } catch (Exception e) {
            log.error("Ошибка при экспорте сущностей: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при экспорте сущностей: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileOperationDto getExportStatus(Long operationId) {
        log.debug("Запрос статуса экспорта для операции: {}", operationId);

        FileOperation operation = fileOperationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Операция экспорта не найдена: " + operationId));

        // Проверяем, что это действительно операция экспорта
        if (operation.getOperationType() != FileOperation.OperationType.EXPORT) {
            throw new IllegalArgumentException("Операция с ID " + operationId + " не является экспортом");
        }

        return mapToDto(operation);
    }

    @Override
    @Transactional
    public boolean cancelExport(Long operationId) {
        log.info("Запрос на отмену экспорта для операции: {}", operationId);

        Optional<FileOperation> optOperation = fileOperationRepository.findById(operationId);
        if (optOperation.isEmpty()) {
            log.warn("Операция экспорта с ID {} не найдена", operationId);
            return false;
        }

        FileOperation operation = optOperation.get();

        // Проверяем, что это операция экспорта
        if (operation.getOperationType() != FileOperation.OperationType.EXPORT) {
            log.warn("Операция с ID {} не является экспортом", operationId);
            return false;
        }

        // Проверяем, что операция не завершена и не отменена
        if (operation.getStatus() == FileOperation.OperationStatus.COMPLETED ||
                operation.getStatus() == FileOperation.OperationStatus.FAILED) {
            log.warn("Операция экспорта {} уже завершена или отменена", operationId);
            return false;
        }

        // Помечаем операцию как неудачную
        operation.markAsFailed("Операция отменена пользователем");
        fileOperationRepository.save(operation);

        // Обновляем информацию в трекере прогресса
        progressTracker.error(operationId, "Операция отменена пользователем");

        log.info("Операция экспорта {} успешно отменена", operationId);
        return true;
    }

    /**
     * Создает новую операцию экспорта
     */
    private FileOperation createExportOperation(Client client, String fileFormat, String entityType) {
        FileFormat format = FileFormat.fromString(fileFormat);
        String fileName = generateFileName(entityType, format.getExtension());

        FileOperation operation = new FileOperation();
        operation.setClient(client);
        operation.setOperationType(FileOperation.OperationType.EXPORT);
        operation.setFileName(fileName);
        operation.setFileType(format.getContentType());
        operation.setEntityType(entityType);
        operation.setStatus(FileOperation.OperationStatus.PENDING);
        operation.setStartedAt(ZonedDateTime.now());
        operation.setProcessingProgress(0);

        return fileOperationRepository.save(operation);
    }

    /**
     * Генерирует имя файла для экспорта
     */
    private String generateFileName(String entityType, String extension) {
        String timestamp = ZonedDateTime.now().toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("export_%s_%s.%s", entityType.toLowerCase(), timestamp, extension);
    }

    /**
     * Получает репозиторий для класса сущности
     */
    @SuppressWarnings("unchecked")
    private <T extends ImportableEntity> JpaRepository<T, ?> getRepositoryForEntityClass(Class<T> entityClass) {
        JpaRepository<T, ?> repository = (JpaRepository<T, ?>) repositories.get(entityClass);
        if (repository == null) {
            throw new IllegalArgumentException("Репозиторий не найден для класса: " + entityClass.getName());
        }
        return repository;
    }

    /**
     * Загружает сущности из БД с применением фильтров
     */
    private <T> List<T> loadEntitiesWithFilter(JpaRepository<T, ?> repository, Map<String, Object> filterCriteria) {
        // Реализация фильтрации данных
        // Пока используем простой findAll, в будущем здесь будет логика применения фильтров

        // Если есть критерии фильтрации для clientId, применяем их
        if (filterCriteria != null && filterCriteria.containsKey("clientId")) {
            Long clientId = Long.valueOf(filterCriteria.get("clientId").toString());

            // Проверяем, какой репозиторий используется и применяем соответствующий метод
            if (repository instanceof ProductRepository) {
                return (List<T>) ((ProductRepository) repository).findByClientId(clientId);
            } else if (repository instanceof RegionDataRepository) {
                return (List<T>) ((RegionDataRepository) repository).findByClientId(clientId);
            } else if (repository instanceof CompetitorDataRepository) {
                return (List<T>) ((CompetitorDataRepository) repository).findByClientId(clientId);
            }
        }

        // Если нет подходящих критериев или репозиторий неизвестен, возвращаем все записи
        return repository.findAll();
    }

    /**
     * Определяет класс сущности по строковому типу
     */
    private Class<? extends ImportableEntity> determineEntityClass(String entityType) {
        if (entityType == null) {
            throw new IllegalArgumentException("Тип сущности не указан");
        }

        // Приводим к нижнему регистру для единообразия
        String type = entityType.toLowerCase();

        // Определяем класс в зависимости от типа
        switch (type) {
            case "product":
                return Product.class;
            case "regiondata":
            case "region":
                return RegionData.class;
            case "competitordata":
            case "competitor":
                return Competitor.class;
            default:
                throw new IllegalArgumentException("Неизвестный тип сущности: " + entityType);
        }
    }

    /**
     * Маппит сущность FileOperation в DTO
     */
    private FileOperationDto mapToDto(FileOperation operation) {
        FileOperationDto dto = new FileOperationDto();
        dto.setId(operation.getId());
        dto.setFileName(operation.getFileName());
        dto.setFileType(operation.getFileType());
        dto.setStatus(operation.getStatus());
        dto.setOperationType(operation.getOperationType());
        dto.setEntityType(operation.getEntityType());
        dto.setStartedAt(operation.getStartedAt());
        dto.setCompletedAt(operation.getCompletedAt());
        dto.setErrorMessage(operation.getErrorMessage());
        dto.setProcessingProgress(operation.getProcessingProgress());
        dto.setClientId(operation.getClient().getId());
        dto.setProcessedRecords(operation.getProcessedRecords());
        dto.setTotalRecords(operation.getTotalRecords());

        // Рассчитываем дополнительные поля
//        if (operation.getStartedAt() != null && operation.getCompletedAt() != null) {
//            long duration = operation.getCompletedAt().toInstant().toEpochMilli() -
//                    operation.getStartedAt().toInstant().toEpochMilli();
//            dto.setDuration(duration);
//        }

        return dto;
    }

    @Override
    @Transactional
    public FileOperationDto exportProductsWithRelatedEntities(
            Client client,
            Map<String, Object> filterCriteria,
            OutputStream outputStream,
            String fileFormat) {

        log.info("Начат экспорт продуктов со связанными сущностями для клиента {}, формат: {}",
                client.getName(), fileFormat);

        try {
            // Создаем запись в БД о новой операции экспорта
            FileOperation operation = createExportOperation(client, fileFormat, "product_with_related");

            // Инициализируем трекер прогресса
            progressTracker.initializeOperation(operation.getId(),
                    "Экспорт продуктов со связанными сущностями");

            // Сохраняем критерии фильтрации в БД
            if (filterCriteria != null && !filterCriteria.isEmpty()) {
                String filterJson = objectMapper.writeValueAsString(filterCriteria);
                operation.setExportFilterCriteria(filterJson);
                operation = fileOperationRepository.save(operation);
            }

            // Получаем продукты на основе критериев фильтрации
            List<Product> products = loadProductsWithFilter(filterCriteria);

            // Обновляем информацию в трекере и операции
            progressTracker.updateStatus(operation.getId(), "Найдено продуктов: " + products.size());
            progressTracker.updateProgress(operation.getId(), 0);

            // Преобразуем продукты в составные сущности
            List<CompositeProductEntity> compositeEntities = compositeExporter.convertToCompositeEntities(products);

            // Обновляем информацию о прогрессе
            progressTracker.updateStatus(operation.getId(),
                    "Подготовлено составных записей: " + compositeEntities.size());
            progressTracker.updateTotal(operation.getId(), compositeEntities.size());

            // Обновляем информацию в БД
            operation.setTotalRecords(compositeEntities.size());
            operation.markAsProcessing();
            operation = fileOperationRepository.save(operation);

            // Создаем конфигурацию экспорта
            ExportConfig config = ExportConfig.createDefault();

            // Получаем процессор для формата файла
            FileFormat format = FileFormat.fromString(fileFormat);
            FileExportProcessor<CompositeProductEntity> processor = processorFactory.createProcessor(format);

            // Выполняем экспорт
            processor.process(compositeEntities, config, outputStream, progressTracker, operation.getId());

            // Обновляем статус операции в БД
            operation.markAsCompleted(compositeEntities.size());
            operation = fileOperationRepository.save(operation);

            return mapToDto(operation);

        } catch (Exception e) {
            log.error("Ошибка при экспорте продуктов со связанными сущностями: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при экспорте данных: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public FileOperationDto exportProductsWithRelatedEntities(
            List<Product> products,
            Client client,
            OutputStream outputStream,
            String fileFormat) {

        log.info("Начат экспорт {} продуктов со связанными сущностями для клиента {}, формат: {}",
                products.size(), client.getName(), fileFormat);

        try {
            // Создаем запись в БД о новой операции экспорта
            FileOperation operation = createExportOperation(client, fileFormat, "product_with_related");

            // Инициализируем трекер прогресса
            progressTracker.initializeOperation(operation.getId(),
                    "Экспорт " + products.size() + " продуктов со связанными сущностями");

            // Преобразуем продукты в составные сущности
            List<CompositeProductEntity> compositeEntities = compositeExporter.convertToCompositeEntities(products);

            // Обновляем информацию о прогрессе
            progressTracker.updateStatus(operation.getId(),
                    "Подготовлено составных записей: " + compositeEntities.size());
            progressTracker.updateTotal(operation.getId(), compositeEntities.size());

            // Обновляем информацию в БД
            operation.setTotalRecords(compositeEntities.size());
            operation.markAsProcessing();
            operation = fileOperationRepository.save(operation);

            return exportCompositeEntities(compositeEntities, client, outputStream, fileFormat, operation);

        } catch (Exception e) {
            log.error("Ошибка при экспорте продуктов со связанными сущностями: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при экспорте данных: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public FileOperationDto exportCompositeEntities(
            List<CompositeProductEntity> compositeEntities,
            Client client,
            OutputStream outputStream,
            String fileFormat) {

        log.info("Начат экспорт {} составных сущностей для клиента {}, формат: {}",
                compositeEntities.size(), client.getName(), fileFormat);

        try {
            // Создаем запись в БД о новой операции экспорта
            FileOperation operation = createExportOperation(client, fileFormat, "product_with_related");

            // Инициализируем трекер прогресса
            progressTracker.initializeOperation(operation.getId(),
                    "Экспорт " + compositeEntities.size() + " составных записей");
            progressTracker.updateTotal(operation.getId(), compositeEntities.size());

            // Обновляем информацию в БД
            operation.setTotalRecords(compositeEntities.size());
            operation.markAsProcessing();
            operation = fileOperationRepository.save(operation);

            return exportCompositeEntities(compositeEntities, client, outputStream, fileFormat, operation);

        } catch (Exception e) {
            log.error("Ошибка при экспорте составных сущностей: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при экспорте данных: " + e.getMessage(), e);
        }
    }

    /**
     * Внутренний метод для экспорта составных сущностей с использованием существующей операции
     */
    private FileOperationDto exportCompositeEntities(
            List<ProductWithRelatedEntitiesExporter.CompositeProductEntity> compositeEntities,
            Client client,
            OutputStream outputStream,
            String fileFormat,
            FileOperation operation) {

        try {
            // Создаем конфигурацию экспорта
            ExportConfig config = ExportConfig.createDefault();

            // Получаем процессор для формата файла
            FileFormat format = FileFormat.fromString(fileFormat);
            FileExportProcessor<ProductWithRelatedEntitiesExporter.CompositeProductEntity> processor = processorFactory.createProcessor(format);

            // Выполняем экспорт
            processor.process(compositeEntities, config, outputStream, progressTracker, operation.getId());

            // Обновляем статус операции в БД
            operation.markAsCompleted(compositeEntities.size());
            operation = fileOperationRepository.save(operation);

            return mapToDto(operation);

        } catch (Exception e) {
            // Обновляем статус операции на ошибку
            operation.markAsFailed("Ошибка при экспорте: " + e.getMessage());
            fileOperationRepository.save(operation);

            log.error("Ошибка при экспорте составных сущностей: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при экспорте данных: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает продукты на основе критериев фильтрации
     */
    private List<Product> loadProductsWithFilter(Map<String, Object> filterCriteria) {
        // Если есть критерии фильтрации для clientId, применяем их
        if (filterCriteria != null && filterCriteria.containsKey("clientId")) {
            Long clientId = Long.valueOf(filterCriteria.get("clientId").toString());
            return productRepository.findByClientId(clientId);
        }

        // По умолчанию возвращаем все продукты
        return productRepository.findAll();
    }
}