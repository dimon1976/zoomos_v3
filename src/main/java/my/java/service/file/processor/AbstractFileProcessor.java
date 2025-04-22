package my.java.service.file.processor;

import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.service.file.options.FileReadingOptions;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.util.PathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Абстрактный класс для процессоров файлов.
 * Реализует общую логику обработки файлов, оставляя специфичную
 * для конкретных типов файлов логику подклассам.
 */
@Slf4j
public abstract class AbstractFileProcessor implements FileProcessor {

    protected final PathResolver pathResolver;
    protected final ValueTransformerFactory transformerFactory;

    /**
     * Конструктор с необходимыми зависимостями.
     *
     * @param pathResolver       утилита для работы с путями
     * @param transformerFactory фабрика трансформеров значений
     */
    protected AbstractFileProcessor(PathResolver pathResolver, ValueTransformerFactory transformerFactory) {
        this.pathResolver = pathResolver;
        this.transformerFactory = transformerFactory;
    }

    public List<ImportableEntity> processFile(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            Map<String, String> params,
            FileOperation operation) {

        try {
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

            // Преобразуем сырые данные в сущности
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

    /**
     * Определяет тип данных для колонки.
     *
     * @param sampleData образец данных
     * @param header заголовок колонки
     * @return определенный тип данных
     */
    protected String detectColumnType(List<Map<String, String>> sampleData, String header) {
        boolean isAllEmpty = true;
        boolean couldBeInteger = true;
        boolean couldBeDouble = true;
        boolean couldBeBoolean = true;
        boolean couldBeDate = true;

        // Проверяем все значения в колонке
        for (Map<String, String> row : sampleData) {
            String value = row.get(header);

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            isAllEmpty = false;
            value = value.trim();

            // Проверяем, является ли значение целым числом
            if (couldBeInteger) {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    couldBeInteger = false;
                }
            }

            // Проверяем, является ли значение дробным числом
            if (couldBeDouble && !couldBeInteger) {
                try {
                    Double.parseDouble(value.replace(",", "."));
                } catch (NumberFormatException e) {
                    couldBeDouble = false;
                }
            }

            // Проверяем, является ли значение булевым
            if (couldBeBoolean) {
                couldBeBoolean = isBooleanValue(value);
            }

            // Проверяем, является ли значение датой
            if (couldBeDate) {
                couldBeDate = isDateValue(value);
            }
        }

        // Определяем тип на основе проверок
        if (isAllEmpty) {
            return "string";
        } else if (couldBeInteger) {
            return "integer";
        } else if (couldBeDouble) {
            return "double";
        } else if (couldBeBoolean) {
            return "boolean";
        } else if (couldBeDate) {
            return "date";
        } else {
            return "string";
        }
    }

    /**
     * Проверяет, является ли значение булевым.
     */
    protected boolean isBooleanValue(String value) {
        String lowerValue = value.toLowerCase();
        return lowerValue.equals("true") || lowerValue.equals("false") ||
                lowerValue.equals("yes") || lowerValue.equals("no") ||
                lowerValue.equals("да") || lowerValue.equals("нет") ||
                lowerValue.equals("1") || lowerValue.equals("0");
    }

    /**
     * Проверяет, является ли значение датой.
     */
    protected boolean isDateValue(String value) {
        // Простая проверка на наличие разделителей дат
        return value.matches(".*\\d+[./\\-]\\d+[./\\-]\\d+.*");
    }

    /**
     * Определяет типы данных в колонках на основе образца данных.
     *
     * @param sampleData образец данных
     * @return карта с типами данных для каждой колонки
     */
    public Map<String, String> detectColumnTypes(List<Map<String, String>> sampleData) {
        if (sampleData == null || sampleData.isEmpty()) {
            return Collections.emptyMap();
        }

        // Получаем список всех заголовков
        Set<String> headers = sampleData.get(0).keySet();
        Map<String, String> columnTypes = new HashMap<>();

        // Для каждой колонки определяем тип данных
        for (String header : headers) {
            String type = detectColumnType(sampleData, header);
            columnTypes.put(header, type);
        }

        return columnTypes;
    }

    public ImportableEntity createEntity(String entityType) {
        if (entityType == null) {
            return null;
        }

        switch (entityType.toLowerCase()) {
            case "product":
                Product product = new Product();
                product.setTransformerFactory(transformerFactory);
                return product;
            case "region":
            case "regiondata":
                Region region = new Region();
                region.setTransformerFactory(transformerFactory);
                return region;
            case "competitor":
            case "competitordata":
                Competitor competitor = new Competitor();
                competitor.setTransformerFactory(transformerFactory);
                return competitor;
            default:
                log.warn("Неизвестный тип сущности: {}", entityType);
                return null;
        }
    }

    @Override
    public Map<String, Object> analyzeFileWithOptions(Path filePath, FileReadingOptions options) {
        log.debug("Анализ файла с FileReadingOptions: {}", filePath);

        // Для обратной совместимости преобразуем options в Map
        Map<String, String> params = options != null ? options.toMap() : null;

        // Вызываем существующий метод анализа
        return analyzeFile(filePath, params);
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


    @Override
    public List<Map<String, String>> readRawData(Path filePath, Map<String, String> params) {
        // Создаем FileReadingOptions из params или используем пустой по умолчанию
        FileReadingOptions options = params != null ?
                FileReadingOptions.fromMap(params) : new FileReadingOptions();

        // Делегируем выполнение readRawDataWithOptions
        return readRawDataWithOptions(filePath, options);
    }

    /**
     * Преобразует сырые данные в сущности с использованием FileReadingOptions.
     *
     * @param rawData      сырые данные из файла
     * @param entityType   тип сущности
     * @param client       клиент
     * @param fieldMapping маппинг полей
     * @param options      параметры обработки
     * @param operation    информация об операции
     * @return список созданных сущностей
     */
    protected List<ImportableEntity> convertToEntitiesWithOptions(
            List<Map<String, String>> rawData,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            FileReadingOptions options,
            FileOperation operation) {

        List<ImportableEntity> entities = new ArrayList<>();
        int totalRecords = rawData.size();
        int processedRecords = 0;
        int successfulRecords = 0;
        List<String> errors = new ArrayList<>();

        // Получаем стратегию обработки ошибок из options
        String errorHandling = options.getErrorHandling();

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
                    String errorMsg = "Не удалось заполнить сущность данными из строки " + processedRecords;
                    errors.add(errorMsg);

                    // Если стратегия "stop", прекращаем обработку при ошибке
                    if ("stop".equals(errorHandling)) {
                        throw new FileOperationException(errorMsg);
                    }

                    continue;
                }

                // Валидация сущности - закомментировано, так как в оригинальном коде тоже было закомментировано
                // String validationError = entity.validate();
                // if (validationError != null) {
                //     errors.add("Ошибка валидации в строке " + processedRecords + ": " + validationError);
                //     continue;
                // }

                // Добавляем сущность в результат
                entities.add(entity);
                successfulRecords++;
            } catch (Exception e) {
                String errorMsg = "Ошибка в строке " + processedRecords + ": " + e.getMessage();
                errors.add(errorMsg);
                log.warn(errorMsg);

                // Если стратегия "stop", прекращаем обработку при ошибке
                if ("stop".equals(errorHandling)) {
                    throw new FileOperationException(errorMsg);
                }
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

            // Если стратегия "report", сохраняем ошибки в additionalParams
            if ("report".equals(errorHandling) && options != null) {
                options.getAdditionalParams().put("errors", String.join("\n", errors));
            }
        }

        return entities;
    }

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

                // Валидируем сущность
//                String validationError = entity.validate();
//                if (validationError != null) {
//                    errors.add("Ошибка валидации в строке " + processedRecords + ": " + validationError);
//                    continue;
//                }

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
            String[] parts = entityField.split("\\.", 2);
            if (parts.length == 2) {
                entityField = parts[1];
            }

            if (rawData.containsKey(fileHeader)) {
                result.put(entityField, rawData.get(fileHeader));
            }
        }

        return result;
    }

    /**
     * Обрабатывает файл с использованием объекта параметров FileReadingOptions.
     *
     * @param filePath путь к файлу
     * @param entityType тип сущности
     * @param client клиент
     * @param fieldMapping маппинг полей
     * @param options параметры чтения файла
     * @param operation операция для отслеживания прогресса
     * @return список созданных сущностей
     */
    public List<ImportableEntity> processFileWithOptions(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            FileReadingOptions options,
            FileOperation operation) {

        try {
            // Вызываем метод для внутренней валидации файла (бросает исключение при проблеме)
            validateFileInternal(filePath);
            log.info("Начало обработки файла с FileReadingOptions: {}, тип сущности: {}, клиент: {}",
                    filePath, entityType, client.getName());

            // Обновляем статус операции
            updateOperationStatus(operation, FileOperation.OperationStatus.PROCESSING);

            // Получаем данные из файла
            List<Map<String, String>> rawData = readRawDataWithOptions(filePath, options);
            log.debug("Прочитано {} записей из файла", rawData.size());

            // Обновляем информацию о количестве записей
            if (operation != null) {
                operation.setTotalRecords(rawData.size());
            }

            // Преобразуем сырые данные в сущности с использованием FileReadingOptions
            List<ImportableEntity> entities = convertToEntitiesWithOptions(
                    rawData, entityType, client, fieldMapping, options, operation);

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
            operation.setProcessingProgress(progress);
            operation.setProcessedRecords(processedRecords);
        }
    }
}