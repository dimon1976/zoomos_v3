package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.service.file.importer.BatchEntityProcessor;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;
import my.java.service.file.importer.EntityRelationshipHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Стратегия игнорирования дубликатов
 * Все записи сохраняются без проверки на дубликаты - каждая строка файла = новая запись в БД
 */
@Slf4j
@RequiredArgsConstructor
@Component
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
        return processCombined(productEntities, relatedEntities, clientId, null);
    }

    @Override
    public BatchSaveResult processCombined(List<ImportableEntity> productEntities,
                                           Map<String, List<ImportableEntity>> relatedEntities,
                                           Long clientId,
                                           EntityRelationshipHolder holder) {

        BatchSaveResult result = new BatchSaveResult();

        log.info("IGNORE strategy: starting processCombined with {} products", productEntities.size());
        log.debug("Related entities: competitors={}, regions={}",
                relatedEntities.get("COMPETITOR") != null ? relatedEntities.get("COMPETITOR").size() : 0,
                relatedEntities.get("REGION") != null ? relatedEntities.get("REGION").size() : 0);

        // Сохраняем все продукты со стратегией IGNORE (которая возвращает ID для каждой записи)
        BatchSaveResult productResult = batchEntityProcessor.saveBatch(
                productEntities, "PRODUCT", DuplicateStrategy.IGNORE);
        result.setSaved(productResult.getSaved());
        result.setFailed(productResult.getFailed());

        log.info("Products saved: {}, failed: {}", productResult.getSaved(), productResult.getFailed());

        // После сохранения продуктов с получением ID, устанавливаем связи
        if (holder != null && productResult.getSaved() > 0) {
            log.info("Setting up relationships for saved products");

            // Создаем маппинг для всех сохраненных продуктов
            Map<String, Long> productIdToDbId = new HashMap<>();

            // Проверяем, что продукты действительно имеют ID
            for (ImportableEntity entity : productEntities) {
                Product product = (Product) entity;
                log.debug("Product after save: productId={}, dbId={}",
                        product.getProductId(), product.getId());

                if (product.getId() != null && product.getProductId() != null) {
                    productIdToDbId.put(product.getProductId(), product.getId());
                }
            }

            log.info("Created productId to DB ID mapping for {} products", productIdToDbId.size());

            if (!productIdToDbId.isEmpty()) {
                // Используем holder для установки связей
                holder.establishDatabaseRelationships(productIdToDbId);
                log.info("Database relationships established via holder");
            } else {
                log.error("No product IDs were generated! Cannot establish relationships");
            }
        } else {
            log.warn("Skipping relationship setup: holder={}, savedProducts={}",
                    holder != null, productResult.getSaved());
        }

        // Сохраняем все связанные сущности (они уже должны иметь установленные связи)
        for (Map.Entry<String, List<ImportableEntity>> entry : relatedEntities.entrySet()) {
            String entityType = entry.getKey();
            List<ImportableEntity> entities = entry.getValue();

            if (!entities.isEmpty()) {
                log.info("IGNORE strategy: saving {} {} entities", entities.size(), entityType);

                // Проверяем связи перед сохранением
                if ("COMPETITOR".equals(entityType)) {
                    for (ImportableEntity e : entities) {
                        Competitor c = (Competitor) e;
                        log.debug("Competitor before save: name={}, productId={}",
                                c.getCompetitorName(),
                                c.getProduct() != null ? c.getProduct().getId() : "null");
                    }
                } else if ("REGION".equals(entityType)) {
                    for (ImportableEntity e : entities) {
                        Region r = (Region) e;
                        log.debug("Region before save: name={}, productId={}",
                                r.getRegion(),
                                r.getProduct() != null ? r.getProduct().getId() : "null");
                    }
                }

                BatchSaveResult relatedResult = batchEntityProcessor.saveBatch(
                        entities, entityType, DuplicateStrategy.IGNORE);
                result.setSaved(result.getSaved() + relatedResult.getSaved());
                result.setFailed(result.getFailed() + relatedResult.getFailed());

                log.info("{} save result: saved={}, failed={}",
                        entityType, relatedResult.getSaved(), relatedResult.getFailed());

                // Добавляем ошибки если есть
                if (!relatedResult.getErrors().isEmpty()) {
                    relatedResult.getErrors().forEach(result::addError);
                    log.error("{} errors: {}", entityType, relatedResult.getErrors());
                }
            }
        }

        log.info("IGNORE strategy completed: total saved={}, total failed={}",
                result.getSaved(), result.getFailed());

        return result;
    }

    @Override
    public DuplicateStrategy getType() {
        return DuplicateStrategy.IGNORE;
    }
}