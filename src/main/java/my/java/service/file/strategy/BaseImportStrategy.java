package my.java.service.file.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.service.file.FieldMappingService;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Базовая стратегия импорта данных
 * @param <T> тип импортируемых данных
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseImportStrategy<T> implements FileProcessingStrategy {

    protected final FieldMappingService fieldMappingService;
    protected final Class<T> entityClass;

    protected Map<String, Field> fieldMapping;
    protected List<String> missingRequiredFields;

    @Override
    public ValidationResult validateHeaders(List<String> headers, Map<String, Object> params) {
        // Создаем сопоставление полей
        fieldMapping = fieldMappingService.createFieldMapping(entityClass, headers);

        // Проверяем обязательные поля
        missingRequiredFields = fieldMappingService.validateRequiredFields(entityClass, headers);

        if (!missingRequiredFields.isEmpty()) {
            return new ValidationResult(false, missingRequiredFields,
                    Collections.singletonList("Отсутствуют обязательные поля: " + String.join(", ", missingRequiredFields)));
        }

        // Проверяем кастомные условия для конкретной стратегии
        return customValidateHeaders(headers, params);
    }

    /**
     * Дополнительная валидация заголовков, специфичная для конкретной стратегии
     *
     * @param headers заголовки файла
     * @param params параметры стратегии
     * @return результат валидации
     */
    protected ValidationResult customValidateHeaders(List<String> headers, Map<String, Object> params) {
        // По умолчанию просто успешная валидация
        return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public ChunkProcessingResult processChunk(List<Map<String, String>> dataChunk, Long operationId, Map<String, Object> params) {
        if (fieldMapping == null) {
            throw new IllegalStateException("Field mapping is not initialized. Call validateHeaders() first.");
        }

        int processedRecords = 0;
        int successRecords = 0;
        int failedRecords = 0;
        List<String> errors = new ArrayList<>();

        // Список созданных сущностей
        List<T> entities = new ArrayList<>();

        // Обрабатываем каждую строку данных
        for (Map<String, String> rowData : dataChunk) {
            try {
                // Создаем сущность из данных строки
                T entity = fieldMappingService.createEntity(entityClass, rowData, fieldMapping);

                // Если сущность создана успешно, добавляем ее в список
                if (entity != null) {
                    // Проверяем дополнительные условия валидации
                    String validationError = validateEntity(entity, rowData);
                    if (validationError != null) {
                        errors.add("Строка " + processedRecords + ": " + validationError);
                        failedRecords++;
                    } else {
                        entities.add(entity);
                        successRecords++;
                    }
                } else {
                    errors.add("Строка " + processedRecords + ": не удалось создать сущность");
                    failedRecords++;
                }
            } catch (Exception e) {
                log.error("Error processing row {}: {}", processedRecords, e.getMessage(), e);
                errors.add("Строка " + processedRecords + ": " + e.getMessage());
                failedRecords++;
            }

            processedRecords++;
        }

        // Сохраняем созданные сущности
        if (!entities.isEmpty()) {
            try {
                saveEntities(entities, operationId, params);
            } catch (Exception e) {
                log.error("Error saving entities: {}", e.getMessage(), e);
                errors.add("Ошибка сохранения данных: " + e.getMessage());

                // Считаем все записи неудачными
                failedRecords = processedRecords;
                successRecords = 0;

                // Указываем, что нужно прекратить обработку
                return new ChunkProcessingResult(processedRecords, 0, processedRecords, errors, false);
            }
        }

        return new ChunkProcessingResult(processedRecords, successRecords, failedRecords, errors, true);
    }

    /**
     * Валидирует созданную сущность
     *
     * @param entity сущность для валидации
     * @param rowData исходные данные строки
     * @return текст ошибки или null, если валидация успешна
     */
    protected String validateEntity(T entity, Map<String, String> rowData) {
        // По умолчанию валидация всегда успешна
        return null;
    }

    /**
     * Сохраняет созданные сущности
     *
     * @param entities список сущностей для сохранения
     * @param operationId идентификатор операции
     * @param params параметры стратегии
     */
    protected abstract void saveEntities(List<T> entities, Long operationId, Map<String, Object> params);

    @Override
    public abstract void rollback(Long operationId, Map<String, Object> params);
}