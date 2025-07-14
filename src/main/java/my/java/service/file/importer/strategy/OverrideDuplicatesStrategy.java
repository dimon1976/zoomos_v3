package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.repository.CompetitorRepository;
import my.java.repository.ProductRepository;
import my.java.repository.RegionRepository;
import my.java.service.file.importer.BatchEntityProcessor;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;
import my.java.service.file.importer.EntityRelationshipHolder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Стратегия перезаписи дубликатов
 * При обнаружении дубликата продукта обновляются все данные включая связанные записи.
 * Берется последнее вхождение productId из файла
 */
@Slf4j
@RequiredArgsConstructor
public class OverrideDuplicatesStrategy implements DuplicateHandlingStrategy {

    private final BatchEntityProcessor batchEntityProcessor;
    private final ProductRepository productRepository;
    private final CompetitorRepository competitorRepository;
    private final RegionRepository regionRepository;

    @Override
    public BatchSaveResult process(List<ImportableEntity> entities, String entityType,
                                   Long clientId, Map<String, Object> existingData) {

        // Для единичного импорта используем стандартную обработку с OVERRIDE
        return batchEntityProcessor.saveBatch(entities, entityType, DuplicateStrategy.OVERRIDE);
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

        log.info("OVERRIDE strategy: processing {} products", productEntities.size());

        // Шаг 1: Обрабатываем продукты с OVERRIDE стратегией
        BatchSaveResult productResult = batchEntityProcessor.saveBatch(
                productEntities, "PRODUCT", DuplicateStrategy.OVERRIDE);

        result.setSaved(productResult.getSaved());
        result.setUpdated(productResult.getUpdated());

        // Шаг 2: Для обновленных продуктов нужно удалить старые связанные записи
        Set<String> processedProductIds = productEntities.stream()
                .map(e -> ((Product) e).getProductId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!processedProductIds.isEmpty()) {
            // Получаем ID продуктов которые были обновлены
            Set<String> updatedProductIds = getUpdatedProductIds(productEntities, clientId);

            if (!updatedProductIds.isEmpty()) {
                log.info("OVERRIDE strategy: deleting old related entities for {} products", updatedProductIds.size());
                deleteOldRelatedEntities(updatedProductIds, clientId);
            }
        }

        // Шаг 3: Устанавливаем связи для новых записей
        if (holder != null) {
            Map<String, Long> productIdToDbId = new HashMap<>();

            // Получаем ID из БД для всех обработанных продуктов
            for (String productId : processedProductIds) {
                productRepository.findByProductIdAndClientId(productId, clientId)
                        .ifPresent(p -> productIdToDbId.put(productId, p.getId()));
            }

            // Устанавливаем связи для связанных сущностей
            // При OVERRIDE берем только последнее вхождение каждого productId
            Map<String, EntityRelationshipHolder.ImportRow> lastRowByProductId = new HashMap<>();
            for (EntityRelationshipHolder.ImportRow row : holder.getAllRows()) {
                if (row.getProductId() != null) {
                    lastRowByProductId.put(row.getProductId(), row);
                }
            }

            for (EntityRelationshipHolder.ImportRow row : lastRowByProductId.values()) {
                Long dbId = productIdToDbId.get(row.getProductId());
                if (dbId != null) {
                    Product productRef = new Product();
                    productRef.setId(dbId);

                    for (ImportableEntity competitor : row.getCompetitors()) {
                        ((my.java.model.entity.Competitor) competitor).setProduct(productRef);
                    }

                    for (ImportableEntity region : row.getRegions()) {
                        ((my.java.model.entity.Region) region).setProduct(productRef);
                    }
                }
            }
        }

        // Шаг 4: Сохраняем новые связанные записи
        for (Map.Entry<String, List<ImportableEntity>> entry : relatedEntities.entrySet()) {
            String entityType = entry.getKey();
            List<ImportableEntity> entities = entry.getValue();

            if (!entities.isEmpty()) {
                log.info("OVERRIDE strategy: saving {} {} entities", entities.size(), entityType);

                BatchSaveResult relatedResult = batchEntityProcessor.saveBatch(
                        entities, entityType, DuplicateStrategy.IGNORE);
                result.setSaved(result.getSaved() + relatedResult.getSaved());
                result.setFailed(result.getFailed() + relatedResult.getFailed());
            }
        }

        log.info("OVERRIDE strategy completed: saved {}, updated {}", result.getSaved(), result.getUpdated());

        return result;
    }

    @Override
    public DuplicateStrategy getType() {
        return DuplicateStrategy.OVERRIDE;
    }

    /**
     * Получение ID обновленных продуктов
     */
    private Set<String> getUpdatedProductIds(List<ImportableEntity> productEntities, Long clientId) {
        Set<String> productIds = productEntities.stream()
                .map(e -> ((Product) e).getProductId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Проверяем, какие из них уже существовали в БД
        List<String> existingIds = productRepository.findExistingProductIds(clientId, new ArrayList<>(productIds));
        return new HashSet<>(existingIds);
    }

    /**
     * Удаление старых связанных записей для обновляемых продуктов
     */
    private void deleteOldRelatedEntities(Set<String> productIds, Long clientId) {
        try {
            // Получаем ID продуктов из БД
            Map<String, Long> productIdToDbId = new HashMap<>();
            for (String productId : productIds) {
                productRepository.findByProductIdAndClientId(productId, clientId)
                        .ifPresent(p -> productIdToDbId.put(productId, p.getId()));
            }

            if (!productIdToDbId.isEmpty()) {
                List<Long> dbIds = new ArrayList<>(productIdToDbId.values());

                // Удаляем старые записи конкурентов
                log.debug("Deleting old competitor records for {} products", dbIds.size());
                competitorRepository.deleteByProductIdIn(dbIds);

                // Удаляем старые записи регионов
                log.debug("Deleting old region records for {} products", dbIds.size());
                regionRepository.deleteByProductIdIn(dbIds);
            }
        } catch (Exception e) {
            log.error("Error deleting old related entities", e);
            // Продолжаем выполнение даже если удаление не удалось
        }
    }
}