package my.java.service.file.strategy.import_strategy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.enums.DataSourceType;
import my.java.service.file.FileProcessingService.FileProcessingResult;
import my.java.service.file.strategy.FileProcessingStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Стратегия импорта данных из файла.
 * Реализует интерфейс FileProcessingStrategy для обработки данных в формате чанков.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ImportFileStrategy implements FileProcessingStrategy {

    @PersistenceContext
    private EntityManager entityManager;

    private final ImportableEntityFactory entityFactory;

    // Кеш для хранения временных данных обработки
    private final Map<Long, ImportContext> importContexts = new HashMap<>();

    /**
     * Контекст импорта для хранения состояния обработки.
     */
    private static class ImportContext {
        private final String entityType;
        private final List<ImportableEntity> entities = new ArrayList<>();
        private int processedRecords = 0;
        private int successRecords = 0;
        private int failedRecords = 0;
        private final List<String> errors = new ArrayList<>();
        private Long clientId;
        private Long fileId;
        private DataSourceType dataSourceType = DataSourceType.FILE;

        public ImportContext(String entityType) {
            this.entityType = entityType;
        }
    }

    @Override
    public String getName() {
        return "ImportFile";
    }

    @Override
    public String getDescription() {
        return "Импорт данных из файла в выбранную сущность";
    }

    @Override
    public ChunkProcessingResult processChunk(List<Map<String, String>> dataChunk, Long operationId,
                                              Map<String, Object> params) {
        // Получаем или создаем контекст импорта
        ImportContext context = getOrCreateContext(operationId, params);

        int processedRecords = 0;
        int successRecords = 0;
        int failedRecords = 0;
        List<String> errors = new ArrayList<>();

        // Обрабатываем каждую строку данных
        for (Map<String, String> rowData : dataChunk) {
            try {
                ImportableEntity entity = entityFactory.createEntity(context.entityType);
                if (entity == null) {
                    errors.add("Не удалось создать сущность типа: " + context.entityType);
                    failedRecords++;
                    continue;
                }

                // Заполняем сущность данными
                boolean fillSuccess = entity.fillFromMap(rowData);

                // Валидируем сущность
                String validationError = entity.validate();

                if (fillSuccess && validationError == null) {
                    // Устанавливаем общие поля
                    setCommonFields(entity, context);

                    // Добавляем сущность в контекст для последующего сохранения
                    context.entities.add(entity);
                    successRecords++;
                } else {
                    String errorMessage = validationError != null ? validationError : "Ошибка заполнения данных";
                    errors.add("Строка " + processedRecords + ": " + errorMessage);
                    failedRecords++;
                }
            } catch (Exception e) {
                log.error("Ошибка обработки строки {}: {}", processedRecords, e.getMessage(), e);
                errors.add("Строка " + processedRecords + ": " + e.getMessage());
                failedRecords++;
            }

            processedRecords++;
        }

        // Если в чанке накопилось много сущностей, сохраняем их и очищаем список
        if (context.entities.size() > 100) {
            saveEntities(context);
        }

        // Обновляем счетчики в контексте
        context.processedRecords += processedRecords;
        context.successRecords += successRecords;
        context.failedRecords += failedRecords;
        context.errors.addAll(errors);

        return new ChunkProcessingResult(
                processedRecords, successRecords, failedRecords, errors, true);
    }

    /**
     * Получает или создает контекст импорта.
     *
     * @param operationId идентификатор операции
     * @param params параметры стратегии
     * @return контекст импорта
     */
    private ImportContext getOrCreateContext(Long operationId, Map<String, Object> params) {
        ImportContext context = importContexts.get(operationId);

        if (context == null) {
            String entityType = getEntityType(params);
            context = new ImportContext(entityType);

            // Устанавливаем параметры контекста
            context.clientId = getClientId(params);
            context.fileId = getFileId(params);
            context.dataSourceType = getDataSourceType(params);

            importContexts.put(operationId, context);
        }

        return context;
    }

    /**
     * Устанавливает общие поля для сущности.
     *
     * @param entity сущность
     * @param context контекст импорта
     */
    private void setCommonFields(ImportableEntity entity, ImportContext context) {
        if (entity instanceof Product) {
            Product product = (Product) entity;
            if (context.clientId != null) {
                product.setClientId(context.clientId);
            }
            if (context.fileId != null) {
                product.setFileId(context.fileId);
            }
            product.setDataSource(context.dataSourceType);
        } else {
            // Устанавливаем clientId для других типов сущностей
            try {
                if (context.clientId != null) {
                    entity.getClass().getMethod("setClientId", Long.class).invoke(entity, context.clientId);
                }
            } catch (Exception e) {
                log.warn("Не удалось установить clientId для сущности {}: {}",
                        entity.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Сохраняет накопленные сущности в базу данных.
     *
     * @param context контекст импорта
     */
    @Transactional
    protected void saveEntities(ImportContext context) {
        for (ImportableEntity entity : context.entities) {
            entityManager.persist(entity);
        }

        // Очищаем список сущностей
        context.entities.clear();

        // Освобождаем память
        entityManager.flush();
        entityManager.clear();
    }

    @Override
    @Transactional
    public FileProcessingResult finishProcessing(Long operationId, Map<String, Object> params) {
        log.debug("Завершение импорта для операции {}", operationId);

        ImportContext context = importContexts.get(operationId);
        if (context == null) {
            return createEmptyResult(operationId);
        }

        // Сохраняем оставшиеся сущности
        if (!context.entities.isEmpty()) {
            saveEntities(context);
        }

        // Создаем результат обработки
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);
        result.setStatus(FileOperation.OperationStatus.COMPLETED);
        result.setMessage(createResultMessage(context));
        result.setProcessedRecords(context.processedRecords);
        result.setSuccessRecords(context.successRecords);
        result.setFailedRecords(context.failedRecords);

        // Добавляем ошибки (ограничиваем количество)
        addErrorsToResult(result, context);

        // Удаляем контекст
        importContexts.remove(operationId);

        return result;
    }

    /**
     * Создает пустой результат обработки.
     *
     * @param operationId идентификатор операции
     * @return пустой результат обработки
     */
    private FileProcessingResult createEmptyResult(Long operationId) {
        FileProcessingResult result = new FileProcessingResult();
        result.setOperationId(operationId);
        result.setStatus(FileOperation.OperationStatus.COMPLETED);
        result.setMessage("Импорт завершен, но не было обработано ни одной записи");
        return result;
    }

    /**
     * Создает сообщение о результате обработки.
     *
     * @param context контекст импорта
     * @return сообщение о результате
     */
    private String createResultMessage(ImportContext context) {
        if (context.failedRecords > 0) {
            return String.format("Импорт завершен с ошибками: обработано %d записей, успешно %d, с ошибками %d",
                    context.processedRecords, context.successRecords, context.failedRecords);
        } else {
            return String.format("Импорт успешно завершен: обработано %d записей", context.processedRecords);
        }
    }

    /**
     * Добавляет ошибки в результат обработки.
     *
     * @param result результат обработки
     * @param context контекст импорта
     */
    private void addErrorsToResult(FileProcessingResult result, ImportContext context) {
        int maxErrors = 100;
        if (!context.errors.isEmpty()) {
            List<String> limitedErrors = context.errors.size() > maxErrors
                    ? context.errors.subList(0, maxErrors)
                    : context.errors;

            for (String error : limitedErrors) {
                result.addError(error);
            }

            if (context.errors.size() > maxErrors) {
                result.addError("... и еще " + (context.errors.size() - maxErrors) + " ошибок");
            }
        }
    }

    @Override
    @Transactional
    public void rollback(Long operationId, Map<String, Object> params) {
        log.debug("Откат импорта для операции {}", operationId);

        ImportContext context = importContexts.get(operationId);
        if (context == null) {
            return;
        }

        // Очищаем контекст
        context.entities.clear();
        importContexts.remove(operationId);

        // Удаляем данные из базы, если был указан fileId
        Long fileId = getFileId(params);
        if (fileId != null) {
            String entityType = getEntityType(params);
            deleteImportedData(fileId, entityType);
        }
    }

    /**
     * Удаляет импортированные данные из базы данных.
     *
     * @param fileId идентификатор файла
     * @param entityType тип сущности
     */
    private void deleteImportedData(Long fileId, String entityType) {
        if ("product".equalsIgnoreCase(entityType)) {
            // Удаляем связанные данные (RegionData и CompetitorData)
            entityManager.createQuery("DELETE FROM RegionData rd WHERE rd.product.fileId = :fileId")
                    .setParameter("fileId", fileId)
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM CompetitorData cd WHERE cd.product.fileId = :fileId")
                    .setParameter("fileId", fileId)
                    .executeUpdate();

            // Удаляем товары
            entityManager.createQuery("DELETE FROM Product p WHERE p.fileId = :fileId")
                    .setParameter("fileId", fileId)
                    .executeUpdate();
        }
    }

    @Override
    public ValidationResult validateHeaders(List<String> headers, Map<String, Object> params) {
        String entityType = getEntityType(params);
        if (entityType == null || !entityFactory.isEntityTypeSupported(entityType)) {
            return new ValidationResult(false, List.of(),
                    List.of("Неподдерживаемый тип сущности: " + entityType));
        }

        ImportableEntity entity = entityFactory.createEntity(entityType);
        if (entity == null) {
            return new ValidationResult(false, List.of(),
                    List.of("Не удалось создать сущность типа: " + entityType));
        }

        // Получаем маппинг полей сущности
        Map<String, String> fieldMappings = entity.getFieldMappings();

        // Проверяем соответствие заголовков полям сущности
        List<String> missingHeaders = new ArrayList<>();
        boolean hasValidHeaders = false;

        for (String header : headers) {
            boolean found = false;
            for (String mappingKey : fieldMappings.keySet()) {
                if (mappingKey.equalsIgnoreCase(header)) {
                    found = true;
                    hasValidHeaders = true;
                    break;
                }
            }

            if (!found) {
                missingHeaders.add(header);
            }
        }

        // Если ни один заголовок не соответствует полям сущности, это ошибка
        if (!hasValidHeaders) {
            return new ValidationResult(false, missingHeaders,
                    List.of("Ни один заголовок не соответствует полям сущности"));
        }

        return new ValidationResult(true, missingHeaders, List.of());
    }

    /**
     * Получает тип сущности из параметров.
     *
     * @param params параметры стратегии
     * @return тип сущности или null, если не указан
     */
    private String getEntityType(Map<String, Object> params) {
        return params != null && params.containsKey("entityType")
                ? params.get("entityType").toString()
                : null;
    }

    /**
     * Получает идентификатор клиента из параметров.
     *
     * @param params параметры стратегии
     * @return идентификатор клиента или null, если не указан
     */
    private Long getClientId(Map<String, Object> params) {
        if (params != null && params.containsKey("clientId")) {
            try {
                return Long.valueOf(params.get("clientId").toString());
            } catch (NumberFormatException e) {
                log.warn("Недопустимый clientId в параметрах: {}", params.get("clientId"));
            }
        }
        return null;
    }

    /**
     * Получает идентификатор файла из параметров.
     *
     * @param params параметры стратегии
     * @return идентификатор файла или null, если не указан
     */
    private Long getFileId(Map<String, Object> params) {
        if (params != null && params.containsKey("fileId")) {
            try {
                return Long.valueOf(params.get("fileId").toString());
            } catch (NumberFormatException e) {
                log.warn("Недопустимый fileId в параметрах: {}", params.get("fileId"));
            }
        }
        return null;
    }

    /**
     * Получает тип источника данных из параметров.
     *
     * @param params параметры стратегии
     * @return тип источника данных или FILE по умолчанию
     */
    private DataSourceType getDataSourceType(Map<String, Object> params) {
        if (params != null && params.containsKey("dataSourceType")) {
            try {
                return DataSourceType.valueOf(params.get("dataSourceType").toString());
            } catch (IllegalArgumentException e) {
                log.warn("Недопустимый dataSourceType в параметрах: {}", params.get("dataSourceType"));
            }
        }
        return DataSourceType.FILE; // По умолчанию
    }
}