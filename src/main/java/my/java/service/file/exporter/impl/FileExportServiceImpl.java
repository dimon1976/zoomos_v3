package my.java.service.file.exporter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;
import my.java.repository.FileOperationRepository;
import my.java.service.file.exporter.ExportConfig;
import my.java.service.file.exporter.FileExportService;
import my.java.service.file.exporter.FileFormat;
import my.java.service.file.exporter.processor.FileExportProcessor;
import my.java.service.file.exporter.processor.FileExportProcessorFactory;
import my.java.service.file.exporter.tracker.ExportProgressTracker;
import my.java.util.PathResolver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Реализация сервиса экспорта файлов
 * Путь: /java/my/java/service/file/exporter/impl/FileExportServiceImpl.java
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileExportServiceImpl implements FileExportService {

    private final Map<Class<? extends ImportableEntity>, JpaRepository<? extends ImportableEntity, ?>> repositories;
    private final FileOperationRepository fileOperationRepository;
    private final ExportProgressTracker progressTracker;
    private final FileExportProcessorFactory processorFactory;
    private final PathResolver pathResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public <T extends ImportableEntity> FileOperationDto exportData(
            Class<T> entityClass,
            Client client,
            Map<String, Object> filterCriteria,
            OutputStream outputStream,
            String fileFormat,
            String entityType) {

        log.info("Начат экспорт данных типа {} для клиента {}, формат: {}",
                entityClass.getSimpleName(), client.getName(), fileFormat);

        try {
            // Создаем запись в БД о новой операции экспорта
            FileOperation operation = createExportOperation(client, fileFormat, entityType);

            // Сохраняем критерии фильтрации
            if (filterCriteria != null && !filterCriteria.isEmpty()) {
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

            // Создаем конфигурацию экспорта
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

            // Создаем конфигурацию экспорта
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
     * Заглушка - в реальной реализации здесь будет логика фильтрации
     */
    private <T> List<T> loadEntitiesWithFilter(JpaRepository<T, ?> repository, Map<String, Object> filterCriteria) {
        // Заглушка - в реальной реализации здесь будет логика применения фильтров
        return repository.findAll();
    }

    /**
     * Определяет класс сущности по строковому типу
     * Заглушка - в реальной реализации здесь будет логика определения класса
     */
    private Class<? extends ImportableEntity> determineEntityClass(String entityType) {
        // Заглушка - в реальной реализации здесь будет логика определения класса
        // по строковому типу сущности
        throw new UnsupportedOperationException("Метод определения класса сущности не реализован");
    }

    /**
     * Маппит сущность FileOperation в DTO
     */
    private FileOperationDto mapToDto(FileOperation operation) {
        // Заглушка - в реальной реализации здесь будет логика маппинга
        FileOperationDto dto = new FileOperationDto();

        // ... заполнение полей DTO ...

        return dto;
    }
}