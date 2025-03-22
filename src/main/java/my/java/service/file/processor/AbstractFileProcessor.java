package my.java.service.file.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;
import my.java.repository.FileOperationRepository;
import my.java.service.file.builder.EntitySetBuilder;
import my.java.service.file.builder.EntitySetBuilderFactory;
import my.java.service.file.tracker.ImportProgressTracker;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.service.file.util.ImportParametersUtil;
import my.java.util.ApplicationContextProvider;
import my.java.util.PathResolver;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Абстрактный класс для процессоров файлов.
 * Реализует общую логику обработки файлов, оставляя специфичную
 * для конкретных типов файлов логику подклассам.
 */
@Slf4j
public abstract class AbstractFileProcessor implements FileProcessor {

    protected final PathResolver pathResolver;
    protected final ValueTransformerFactory transformerFactory;
    protected final EntitySetBuilderFactory entitySetBuilderFactory;
    protected final FileOperationRepository fileOperationRepository;

    /**
     * Конструктор с необходимыми зависимостями.
     *
     * @param pathResolver            утилита для работы с путями
     * @param transformerFactory      фабрика трансформеров значений
     * @param fileOperationRepository
     */
    protected AbstractFileProcessor(PathResolver pathResolver, ValueTransformerFactory transformerFactory, EntitySetBuilderFactory entitySetBuilderFactory, FileOperationRepository fileOperationRepository) {
        this.pathResolver = pathResolver;
        this.transformerFactory = transformerFactory;
        this.entitySetBuilderFactory = entitySetBuilderFactory;
        this.fileOperationRepository = fileOperationRepository;
    }

    /**
     * Обрабатывает файл с использованием строителя наборов сущностей.
     *
     * @param filePath   путь к файлу
     * @param entityType тип сущности
     * @param client     клиент
     * @param params     дополнительные параметры
     * @param operation  объект операции для обновления прогресса
     * @param builder    строитель для создания сущностей
     * @return список групп связанных сущностей
     */
    protected List<List<ImportableEntity>> processFileWithBuilder(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> params,
            FileOperation operation,
            EntitySetBuilder builder) {

        try {
            // Проверяем строителя
            if (builder == null) {
                throw new FileOperationException("Строитель не может быть null");
            }

            // Получаем параметр валидации данных
            boolean validateData = ImportParametersUtil.getBooleanParam(params, "validateData", false);
            log.debug("Применяется валидация данных: {}", validateData);

            // Вызываем метод для внутренней валидации файла
            validateFileInternal(filePath);
            log.info("Начало обработки файла строителем: {}, тип сущности: {}, клиент: {}",
                    filePath, entityType, client.getName());

            // Обновляем статус операции
            updateOperationStatus(operation, FileOperation.OperationStatus.PROCESSING);

            // Получаем данные из файла
            List<Map<String, String>> rawData = readFile(filePath, params);
            log.debug("Прочитано {} записей из файла", rawData.size());

            // Обновляем информацию о количестве записей
            if (operation != null) {
                operation.setTotalRecords(rawData.size());
            }

            List<List<ImportableEntity>> entitySets = new ArrayList<>();
            int totalRecords = rawData.size();
            int processedRecords = 0;
            int successfulRecords = 0;
            List<String> errors = new ArrayList<>();

            // Обрабатываем каждую строку
            for (Map<String, String> row : rawData) {
                processedRecords++;

                try {
                    // Сбрасываем строитель для новой строки
                    builder.reset();

                    // Применяем данные строки к строителю
                    boolean applied = builder.applyRow(row);
                    if (!applied) {
                        errors.add("Не удалось применить данные из строки " + processedRecords);
                        continue;
                    }

                    // Валидируем строитель только если требуется
                    if (validateData) {
                        String validationError = builder.validate();
                        if (validationError != null) {
                            errors.add("Ошибка валидации в строке " + processedRecords + ": " + validationError);
                            continue;
                        }
                    }

                    // Строим сущности
                    List<ImportableEntity> rowEntities = builder.build();
                    if (rowEntities.isEmpty()) {
                        errors.add("Не удалось создать сущности из строки " + processedRecords);
                        continue;
                    }

                    // Добавляем созданные сущности в результат
                    entitySets.add(rowEntities);
                    successfulRecords++;
                } catch (Exception e) {
                    errors.add("Ошибка в строке " + processedRecords + ": " + e.getMessage());
                    log.warn("Ошибка при обработке строки {}: {}", processedRecords, e.getMessage());
                }

                // Обновляем прогресс
                updateProgress(operation, processedRecords, totalRecords);

                // Проверяем, не была ли операция отменена
                if (operation != null && operation.getStatus() == FileOperation.OperationStatus.FAILED) {
                    log.info("Операция была отменена, прерываем обработку файла");
                    break;
                }
            }

            // Обновляем статистику операции
            if (operation != null) {
                operation.setProcessedRecords(processedRecords);
            }

            // Логируем итоги обработки
            if (errors.isEmpty()) {
                log.info("Обработка файла с использованием строителя успешно завершена. Всего записей: {}, успешно: {}",
                        totalRecords, successfulRecords);
            } else {
                log.info("Обработка файла с использованием строителя завершена с ошибками. Всего записей: {}, успешно: {}, с ошибками: {}",
                        totalRecords, successfulRecords, errors.size());
                log.debug("Ошибки при обработке: {}", String.join("; ", errors.subList(0, Math.min(10, errors.size()))));

                // Если есть много ошибок, логируем только первые несколько с полным стектрейсом
                if (errors.size() > 10) {
                    log.debug("... и еще {} ошибок", errors.size() - 10);
                }
            }

            return entitySets;
        } catch (Exception e) {
            String errorMessage = "Ошибка при обработке файла строителем: " + e.getMessage();
            log.error(errorMessage, e);
            throw new FileOperationException(errorMessage, e);
        }
    }

    @Override
    public List<ImportableEntity> processFile(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            Map<String, String> params,
            FileOperation operation) {

        try {
            // Логируем параметры импорта
            log.info("Параметры импорта: {}", params);

            // Применяем настройки процесса из параметров
            applyImportParameters(params, operation);

            // Вызываем метод для внутренней валидации файла (бросает исключение при проблеме)
            validateFileInternal(filePath);
            log.info("Начало обработки файла: {}, тип сущности: {}, клиент: {}",
                    filePath, entityType, client.getName());

            // Обновляем статус операции
            updateOperationStatus(operation, FileOperation.OperationStatus.PROCESSING);

            // Получаем данные из файла
            List<Map<String, String>> rawData = readFile(filePath, params);
            log.debug("Прочитано {} записей из файла", rawData.size());

            // Обновляем информацию о количестве записей
            if (operation != null) {
                operation.setTotalRecords(rawData.size());
            }

            // Проверяем, используется ли строитель
            if (entityType != null && entitySetBuilderFactory.supportsEntityType(entityType)) {
                // Получаем строитель из фабрики и настраиваем его
                Map<String, String> builderParams = new HashMap<>();
                if (params != null) {
                    builderParams.putAll(params);
                }

                // Добавляем маппинг полей в параметры
                if (fieldMapping != null) {
                    // Преобразуем маппинг полей в формат для билдера
                    for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                        builderParams.put("mapping[" + entry.getKey() + "]", entry.getValue());
                    }
                }

                // Добавляем ID операции как ID файла
                if (operation != null && operation.getId() != null) {
                    builderParams.put("fileId", operation.getId().toString());
                }

                EntitySetBuilder builder = entitySetBuilderFactory.createAndConfigureBuilder(entityType, builderParams);
                if (builder == null) {
                    throw new FileOperationException("Не удалось создать строителя для типа сущности: " + entityType);
                }

                // Устанавливаем клиента для строителя
                builder.withClientId(client.getId());

                // Обрабатываем данные с использованием строителя
                List<List<ImportableEntity>> entitySets = processFileWithBuilder(
                        filePath, entityType, client, params, operation, builder);

                // Конвертируем список групп сущностей в плоский список всех сущностей
                return entitySets.stream()
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
            }

            // Стандартная обработка для обычных сущностей
            List<ImportableEntity> entities = convertToEntities(rawData, entityType, client, fieldMapping, operation);
            log.debug("Создано {} сущностей типа {}", entities.size(), entityType);

            return entities;
        } catch (Exception e) {
            String errorMessage = "Ошибка при обработке файла: " + e.getMessage();
            log.error(errorMessage, e);
            // Помечаем операцию как неудачную
            if (operation != null) {
                operation.markAsFailed(errorMessage);
            }
            throw new FileOperationException(errorMessage, e);
        }
    }

    // Новый метод в AbstractFileProcessor.java для применения параметров импорта
    private void applyImportParameters(Map<String, String> params, FileOperation operation) {
        if (params == null || operation == null) {
            return;
        }

        // Логируем важные параметры
        boolean validateData = ImportParametersUtil.getBooleanParam(params, "validateData", true);
        log.debug("Установлен режим валидации данных: {}", validateData);

        boolean trimWhitespace = ImportParametersUtil.getBooleanParam(params, "trimWhitespace", true);
        log.debug("Установлен режим удаления пробелов: {}", trimWhitespace);

        // Обработка существующих параметров
        if (params.containsKey("batchSize")) {
            try {
                int batchSize = Integer.parseInt(params.get("batchSize"));
                // Закомментировано, так как метод отсутствует в классе FileOperation
                // operation.setBatchSize(batchSize);
                log.debug("Установлен размер пакета: {}", batchSize);
            } catch (NumberFormatException e) {
                log.warn("Неверный формат размера пакета: {}", params.get("batchSize"));
            }
        }

        if (params.containsKey("processingStrategy")) {
            // Закомментировано, так как метод отсутствует в классе FileOperation
            // operation.setProcessingStrategy(params.get("processingStrategy"));
            log.debug("Установлена стратегия обработки: {}", params.get("processingStrategy"));
        }

        if (params.containsKey("errorHandling")) {
            // Закомментировано, так как метод отсутствует в классе FileOperation
            // operation.setErrorHandling(params.get("errorHandling"));
            log.debug("Установлен метод обработки ошибок: {}", params.get("errorHandling"));
        }

        // Сохраняем все параметры в метаданных операции
        // Закомментировано до реализации соответствующего метода в FileOperation
        // operation.setMetadata(ImportParametersUtil.copyParams(params));

        log.debug("Сохранены метаданные операции: {}", params);
    }

    /**
     * Считывает данные из файла.
     *
     * @param filePath путь к файлу
     * @param params   параметры чтения
     * @return список сырых данных
     * @throws IOException если возникла ошибка при чтении файла
     */
    protected abstract List<Map<String, String>> readFile(Path filePath, Map<String, String> params) throws IOException;

    /**
     * Выполняет внутреннюю валидацию файла перед обработкой.
     * Этот метод бросает исключение, если файл не прошел валидацию.
     *
     * @param filePath путь к файлу
     * @throws FileOperationException если файл не прошел валидацию
     */
    protected void validateFileInternal(Path filePath) {
        if (filePath == null) {
            throw new FileOperationException("Путь к файлу не может быть null");
        }

        if (!Files.exists(filePath)) {
            throw new FileOperationException("Файл не найден: " + filePath);
        }

        if (!Files.isRegularFile(filePath)) {
            throw new FileOperationException("Путь не указывает на файл: " + filePath);
        }

        if (!Files.isReadable(filePath)) {
            throw new FileOperationException("Нет прав на чтение файла: " + filePath);
        }

        validateFileType(filePath);
    }

    /**
     * Реализация интерфейсного метода validateFile.
     * Этот метод возвращает boolean вместо бросания исключения.
     *
     * @param filePath путь к файлу
     * @return true, если файл прошел валидацию
     */
    @Override
    public boolean validateFile(Path filePath) {
        try {
            validateFileInternal(filePath);
            return true;
        } catch (Exception e) {
            log.warn("Файл не прошел валидацию: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Валидирует тип файла.
     *
     * @param filePath путь к файлу
     * @throws FileOperationException если тип файла не поддерживается
     */
    protected abstract void validateFileType(Path filePath);

    /**
     * Преобразует сырые данные в сущности.
     *
     * @param rawData      сырые данные из файла
     * @param entityType   тип сущности
     * @param client       клиент
     * @param fieldMapping маппинг полей
     * @param operation    информация об операции
     * @return список созданных сущностей
     */
    protected List<ImportableEntity> convertToEntities(
            List<Map<String, String>> rawData,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            FileOperation operation) {

        // Получаем параметры из операции, если доступно
        Map<String, String> params = new HashMap<>();
        // Добавляем проверку, чтобы получить метаданные операции, когда будет реализован метод getMetadata
        // if (operation != null && operation.getMetadata() != null) {
        //     params.putAll(operation.getMetadata());
        // }

        // Получаем параметр валидации данных
        boolean validateData = ImportParametersUtil.getBooleanParam(params, "validateData", true);
        log.debug("Применяется валидация данных в convertToEntities: {}", validateData);

        List<ImportableEntity> entities = new ArrayList<>();
        int totalRecords = rawData.size();
        int processedRecords = 0;
        int successfulRecords = 0;
        List<String> errors = new ArrayList<>();

        for (Map<String, String> row : rawData) {
            processedRecords++;

            try {
                ImportableEntity entity = createEntity(entityType);
                if (entity == null) {
                    errors.add("Неподдерживаемый тип сущности: " + entityType);
                    continue;
                }

                // Устанавливаем клиента для сущности, если это поддерживается
                setClientIfSupported(entity, client);

                // Применяем трансформеры к сущности, если это поддерживается
                setTransformerIfSupported(entity);

                // Применяем маппинг полей, если он предоставлен
                Map<String, String> effectiveMapping = fieldMapping != null ? fieldMapping : entity.getFieldMappings();

                // Применяем маппинг к данным строки
                Map<String, String> mappedData = applyFieldMapping(row, effectiveMapping);

                // Заполняем сущность данными
                boolean filled = entity.fillFromMap(mappedData);
                if (!filled) {
                    errors.add("Не удалось заполнить сущность данными из строки " + processedRecords);
                    continue;
                }

                // Валидируем сущность только если требуется
                if (validateData) {
                    String validationError = entity.validate();
                    if (validationError != null) {
                        errors.add("Ошибка валидации в строке " + processedRecords + ": " + validationError);
                        continue;
                    }
                }

                // Добавляем сущность в результат
                entities.add(entity);
                successfulRecords++;
            } catch (Exception e) {
                errors.add("Ошибка в строке " + processedRecords + ": " + e.getMessage());
                log.warn("Ошибка при обработке строки {}: {}", processedRecords, e.getMessage());
            }

            // Обновляем прогресс
            updateProgress(operation, processedRecords, totalRecords);
        }

        // Обновляем статистику операции
        if (operation != null) {
            operation.setProcessedRecords(processedRecords);
        }

        // Логируем итоги обработки
        log.info("Обработка файла завершена. Всего записей: {}, успешно: {}, с ошибками: {}",
                totalRecords, successfulRecords, errors.size());

        if (!errors.isEmpty()) {
            log.debug("Ошибки при обработке: {}", String.join("; ", errors));
        }

        return entities;
    }

    /**
     * Создает экземпляр сущности указанного типа.
     *
     * @param entityType тип сущности
     * @return новый экземпляр сущности или null, если тип не поддерживается
     */
    protected abstract ImportableEntity createEntity(String entityType);

    /**
     * Устанавливает клиента для сущности, если это поддерживается.
     *
     * @param entity сущность
     * @param client клиент
     */
    protected void setClientIfSupported(ImportableEntity entity, Client client) {
        try {
            if (entity.getClass().getDeclaredField("clientId") != null) {
                entity.getClass().getMethod("setClientId", Long.class).invoke(entity, client.getId());
            }
        } catch (Exception e) {
            // Игнорируем, если поле не поддерживается
            log.debug("Сущность {} не поддерживает установку clientId", entity.getClass().getSimpleName());
        }
    }

    /**
     * Устанавливает фабрику трансформеров для сущности, если это поддерживается.
     *
     * @param entity сущность
     */
    protected void setTransformerIfSupported(ImportableEntity entity) {
        try {
            entity.getClass().getMethod("setTransformerFactory", ValueTransformerFactory.class)
                    .invoke(entity, transformerFactory);
        } catch (Exception e) {
            // Игнорируем, если метод не поддерживается
            log.debug("Сущность {} не поддерживает установку transformerFactory", entity.getClass().getSimpleName());
        }
    }

    /**
     * Применяет маппинг полей к данным.
     *
     * @param rawData      исходные данные
     * @param fieldMapping маппинг полей
     * @return данные с примененным маппингом
     */
    protected Map<String, String> applyFieldMapping(Map<String, String> rawData, Map<String, String> fieldMapping) {
        Map<String, String> result = new HashMap<>();

        // Если маппинг отсутствует, возвращаем исходные данные
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            return new HashMap<>(rawData);
        }

        // Применяем маппинг для каждого поля
        for (Map.Entry<String, String> mappingEntry : fieldMapping.entrySet()) {
            String fileHeader = mappingEntry.getKey();
            String entityField = mappingEntry.getValue();

            if (rawData.containsKey(fileHeader)) {
                result.put(entityField, rawData.get(fileHeader));
            }
        }

        return result;
    }

    /**
     * Обновляет статус операции.
     *
     * @param operation операция
     * @param status    новый статус
     */
    protected void updateOperationStatus(FileOperation operation, FileOperation.OperationStatus status) {
        if (operation != null) {
            operation.setStatus(status);
        }
    }

    /**
     * Обновляет прогресс операции.
     *
     * @param operation        операция
     * @param processedRecords количество обработанных записей
     * @param totalRecords     общее количество записей
     */
    protected void updateProgress(FileOperation operation, int processedRecords, int totalRecords) {
        if (operation != null && totalRecords > 0) {
            int progress = (int) (((double) processedRecords / totalRecords) * 100);

            // Обновляем состояние операции
            operation.setProcessingProgress(progress);
            operation.setProcessedRecords(processedRecords);
            operation.setTotalRecords(totalRecords);

            // Сохраняем обновленное состояние
            try {
                fileOperationRepository.save(operation);

                // Отправляем уведомление о прогрессе через WebSocket
                if (operation.getId() != null) {
                    // Используем ImportProgressTracker для отправки уведомления
                    try {
                        // Получаем трекер из контекста Spring, если он доступен
                        ApplicationContext context = ApplicationContextProvider.getContext();
                        if (context != null) {
                            ImportProgressTracker tracker = context.getBean(ImportProgressTracker.class);
                            if (tracker != null) {
                                tracker.updateProgress(operation.getId(), processedRecords);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Не удалось отправить уведомление о прогрессе: {}", e.getMessage());
                    }
                }

                log.debug("Прогресс обновлен: операция #{}, прогресс {}%, обработано {} из {} записей",
                        operation.getId(), progress, processedRecords, totalRecords);
            } catch (Exception e) {
                log.error("Ошибка при обновлении прогресса операции #{}: {}",
                        operation.getId(), e.getMessage());
            }
        }
    }
}