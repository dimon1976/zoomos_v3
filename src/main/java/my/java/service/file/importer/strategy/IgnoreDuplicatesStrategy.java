package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.service.file.importer.BatchEntityProcessor;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;

import java.util.List;
import java.util.Map;

/**
 * Стратегия игнорирования дубликатов
 * Все записи сохраняются без проверки на дубликаты
 */
@Slf4j
@RequiredArgsConstructor
public class IgnoreDuplicatesStrategy implements DuplicateHandlingStrategy {

    private final BatchEntityProcessor batchEntityProcessor;

    @Override
    public BatchSaveResult process(List<ImportableEntity> entities, String entityType,
                                   Long clientId, Map<String, Object> existingData) {

        // Просто сохраняем все записи без проверок
        return batchEntityProcessor.saveBatch(entities, entityType, DuplicateStrategy.IGNORE);
    }

    @Override
    public BatchSaveResult processCombined(List<ImportableEntity> productEntities,
                                           Map<String, List<ImportableEntity>> relatedEntities,
                                           Long clientId) {

        BatchSaveResult result = new BatchSaveResult();

        // Сохраняем все продукты
        BatchSaveResult productResult = batchEntityProcessor.saveBatch(
                productEntities, "PRODUCT", DuplicateStrategy.IGNORE);
        result.setSaved(productResult.getSaved());

        // Сохраняем все связанные сущности
        for (Map.Entry<String, List<ImportableEntity>> entry : relatedEntities.entrySet()) {
            String entityType = entry.getKey();
            List<ImportableEntity> entities = entry.getValue();

            if (!entities.isEmpty()) {
                BatchSaveResult relatedResult = batchEntityProcessor.saveBatch(
                        entities, entityType, DuplicateStrategy.IGNORE);
                result.setSaved(result.getSaved() + relatedResult.getSaved());
            }
        }

        return result;
    }

    @Override
    public DuplicateStrategy getType() {
        return DuplicateStrategy.IGNORE;
    }
}