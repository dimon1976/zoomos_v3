// src/main/java/my/java/service/file/processor/AbstractFileProcessor.java
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
 */
@Slf4j
public abstract class AbstractFileProcessor implements FileProcessor {

    protected final PathResolver pathResolver;
    protected final ValueTransformerFactory transformerFactory;

    protected AbstractFileProcessor(PathResolver pathResolver, ValueTransformerFactory transformerFactory) {
        this.pathResolver = pathResolver;
        this.transformerFactory = transformerFactory;
    }

    /**
     * Считывает данные из файла.
     */
    protected abstract List<Map<String, String>> readFileWithOptions(Path filePath, FileReadingOptions options) throws IOException;

    /**
     * Выполняет внутреннюю валидацию файла перед обработкой.
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
     */
    protected abstract void validateFileType(Path filePath);

    @Override
    public List<Map<String, String>> readRawDataWithOptions(Path filePath, FileReadingOptions options) {
        try {
            return readFileWithOptions(filePath, options != null ? options : new FileReadingOptions());
        } catch (IOException e) {
            throw new FileOperationException("Ошибка при чтении данных: " + e.getMessage(), e);
        }
    }

    /**
     * Преобразует сырые данные в сущности с обработкой ошибок.
     */
    protected List<ImportableEntity> convertToEntities(
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
        String errorHandling = options.getErrorHandling();

        for (Map<String, String> row : rawData) {
            processedRecords++;

            try {
                ImportableEntity entity = createEntity(entityType);
                if (entity == null) {
                    errors.add("Неподдерживаемый тип сущности: " + entityType);
                    continue;
                }

                // Устанавливаем клиента и трансформер
                setClientIfSupported(entity, client);
                setTransformerIfSupported(entity);

                // Применяем маппинг полей
                Map<String, String> effectiveMapping = fieldMapping != null ?
                        fieldMapping : entity.getFieldMappings();
                Map<String, String> mappedData = applyFieldMapping(row, effectiveMapping);

                // Заполняем сущность данными
                if (!entity.fillFromMap(mappedData)) {
                    String errorMsg = "Не удалось заполнить сущность данными из строки " + processedRecords;
                    errors.add(errorMsg);
                    if ("stop".equals(errorHandling)) {
                        throw new FileOperationException(errorMsg);
                    }
                    continue;
                }

                entities.add(entity);
                successfulRecords++;
            } catch (Exception e) {
                String errorMsg = "Ошибка в строке " + processedRecords + ": " + e.getMessage();
                errors.add(errorMsg);
                log.warn(errorMsg);

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

        // Если стратегия "report", сохраняем ошибки
        if (!errors.isEmpty() && "report".equals(errorHandling) && options != null) {
            options.getAdditionalParams().put("errors", String.join("\n", errors));
        }

        return entities;
    }

    @Override
    public List<ImportableEntity> processFileWithOptions(
            Path filePath,
            String entityType,
            Client client,
            Map<String, String> fieldMapping,
            FileReadingOptions options,
            FileOperation operation) {

        try {
            validateFileInternal(filePath);

            // Обновляем статус операции
            updateOperationStatus(operation, FileOperation.OperationStatus.PROCESSING);

            // Получаем данные из файла
            List<Map<String, String>> rawData = readRawDataWithOptions(filePath, options);

            // Обновляем информацию о количестве записей
            if (operation != null) {
                operation.setTotalRecords(rawData.size());
            }

            // Преобразуем сырые данные в сущности
            return convertToEntities(rawData, entityType, client, fieldMapping, options, operation);
        } catch (Exception e) {
            String errorMessage = "Ошибка при обработке файла: " + e.getMessage();
            log.error(errorMessage, e);
            if (operation != null) {
                operation.markAsFailed(errorMessage);
            }
            throw new FileOperationException(errorMessage, e);
        }
    }

    // Остальные вспомогательные методы остаются без изменений

    @Override
    public ImportableEntity createEntity(String entityType) {
        if (entityType == null) return null;

        ImportableEntity entity = switch (entityType.toLowerCase()) {
            case "product" -> new Product();
            case "region", "regiondata" -> new Region();
            case "competitor", "competitordata" -> new Competitor();
            default -> {
                log.warn("Неизвестный тип сущности: {}", entityType);
                yield null;
            }
        };

        if (entity != null) {
            setTransformerIfSupported(entity);
        }

        return entity;
    }

    protected void setClientIfSupported(ImportableEntity entity, Client client) {
        try {
            if (entity.getClass().getDeclaredField("clientId") != null) {
                entity.getClass().getMethod("setClientId", Long.class).invoke(entity, client.getId());
            }
        } catch (Exception ignored) {
            // Игнорируем, если поле не поддерживается
        }
    }

    protected void setTransformerIfSupported(ImportableEntity entity) {
        try {
            entity.getClass().getMethod("setTransformerFactory", ValueTransformerFactory.class)
                    .invoke(entity, transformerFactory);
        } catch (Exception ignored) {
            // Игнорируем, если метод не поддерживается
        }
    }

    protected Map<String, String> applyFieldMapping(Map<String, String> rawData, Map<String, String> fieldMapping) {
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            return new HashMap<>(rawData);
        }

        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, String> mappingEntry : fieldMapping.entrySet()) {
            String fileHeader = mappingEntry.getKey();
            String entityField = mappingEntry.getValue();

            // Обрабатываем поля с префиксом
            if (entityField.contains(".")) {
                String[] parts = entityField.split("\\.", 2);
                if (parts.length == 2) {
                    entityField = parts[1];
                }
            }

            if (rawData.containsKey(fileHeader)) {
                result.put(entityField, rawData.get(fileHeader));
            }
        }

        return result;
    }

    protected void updateOperationStatus(FileOperation operation, FileOperation.OperationStatus status) {
        if (operation != null) {
            operation.setStatus(status);
        }
    }

    protected void updateProgress(FileOperation operation, int processedRecords, int totalRecords) {
        if (operation != null && totalRecords > 0) {
            int progress = (int) (((double) processedRecords / totalRecords) * 100);
            operation.setProcessingProgress(progress);
            operation.setProcessedRecords(processedRecords);
        }
    }

    // Методы для определения типов данных

    protected String detectColumnType(List<Map<String, String>> sampleData, String header) {
        boolean isAllEmpty = true;
        boolean couldBeInteger = true;
        boolean couldBeDouble = true;
        boolean couldBeBoolean = true;
        boolean couldBeDate = true;

        for (Map<String, String> row : sampleData) {
            String value = row.get(header);

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            isAllEmpty = false;
            value = value.trim();

            // Проверяем тип Integer
            if (couldBeInteger && !value.matches("-?\\d+")) {
                couldBeInteger = false;
            }

            // Проверяем тип Double
            if (couldBeDouble && !couldBeInteger &&
                    !value.matches("-?\\d+([.,]\\d+)?")) {
                couldBeDouble = false;
            }

            // Проверяем Boolean
            if (couldBeBoolean && !isBooleanValue(value)) {
                couldBeBoolean = false;
            }

            // Проверяем Date
            if (couldBeDate && !isDateValue(value)) {
                couldBeDate = false;
            }
        }

        // Определяем тип на основе проверок
        if (isAllEmpty) return "string";
        if (couldBeInteger) return "integer";
        if (couldBeDouble) return "double";
        if (couldBeBoolean) return "boolean";
        if (couldBeDate) return "date";
        return "string";
    }

    protected boolean isBooleanValue(String value) {
        String lowerValue = value.toLowerCase();
        return lowerValue.equals("true") || lowerValue.equals("false") ||
                lowerValue.equals("yes") || lowerValue.equals("no") ||
                lowerValue.equals("да") || lowerValue.equals("нет") ||
                lowerValue.equals("1") || lowerValue.equals("0");
    }

    protected boolean isDateValue(String value) {
        return value.matches(".*\\d+[./\\-]\\d+[./\\-]\\d+.*");
    }

    public Map<String, String> detectColumnTypes(List<Map<String, String>> sampleData) {
        if (sampleData == null || sampleData.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> columnTypes = new HashMap<>();
        Set<String> headers = sampleData.get(0).keySet();

        for (String header : headers) {
            columnTypes.put(header, detectColumnType(sampleData, header));
        }

        return columnTypes;
    }
}