package my.java.service.file.importer.strategy;

import my.java.model.entity.ImportableEntity;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;
import my.java.service.file.importer.EntityRelationshipHolder;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс для стратегий обработки дубликатов при импорте
 */
public interface DuplicateHandlingStrategy {

    /**
     * Обработка пакета сущностей с учетом дубликатов
     *
     * @param entities сущности для обработки
     * @param entityType тип сущности (PRODUCT, COMPETITOR, REGION)
     * @param clientId идентификатор клиента
     * @param existingData карта существующих данных для проверки дубликатов
     * @return результат обработки пакета
     */
    BatchSaveResult process(
            List<ImportableEntity> entities,
            String entityType,
            Long clientId,
            Map<String, Object> existingData
    );

    /**
     * Обработка связанных сущностей для COMBINED импорта
     *
     * @param productEntities основные сущности (продукты)
     * @param relatedEntities связанные сущности (конкуренты/регионы)
     * @param clientId идентификатор клиента
     * @return результат обработки всех сущностей
     */
    BatchSaveResult processCombined(
            List<ImportableEntity> productEntities,
            Map<String, List<ImportableEntity>> relatedEntities,
            Long clientId
    );

    /**
     * Обработка связанных сущностей для COMBINED импорта с использованием EntityRelationshipHolder
     *
     * @param productEntities основные сущности (продукты)
     * @param relatedEntities связанные сущности (конкуренты/регионы)
     * @param clientId идентификатор клиента
     * @param holder держатель связей между сущностями
     * @return результат обработки всех сущностей
     */
    default BatchSaveResult processCombined(
            List<ImportableEntity> productEntities,
            Map<String, List<ImportableEntity>> relatedEntities,
            Long clientId,
            EntityRelationshipHolder holder
    ) {
        // По умолчанию вызываем метод без holder для обратной совместимости
        return processCombined(productEntities, relatedEntities, clientId);
    }

    /**
     * Получить тип стратегии
     */
    DuplicateStrategy getType();
}