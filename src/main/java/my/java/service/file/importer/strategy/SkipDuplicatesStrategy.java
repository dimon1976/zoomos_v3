package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.repository.ProductRepository;
import my.java.service.file.importer.BatchEntityProcessor;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;
import my.java.service.file.importer.EntityRelationshipHolder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Стратегия пропуска дубликатов
 * При обнаружении дубликата продукта пропускается вся строка (продукт + связанные записи)
 */
@Slf4j
@RequiredArgsConstructor
public class SkipDuplicatesStrategy implements DuplicateHandlingStrategy {

    private final BatchEntityProcessor batchEntityProcessor;
    private final ProductRepository productRepository;

    @Override
    public BatchSaveResult process(List<ImportableEntity> entities, String entityType,
                                   Long clientId, Map<String, Object> existingData) {

        BatchSaveResult result = new BatchSaveResult();

        if (entities.isEmpty()) {
            return result;
        }

        if ("PRODUCT".equals(entityType)) {
            return processProducts(entities, clientId);
        } else {
            // Для других типов сущностей просто сохраняем
            return batchEntityProcessor.saveBatch(entities, entityType, DuplicateStrategy.IGNORE);
        }
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

        log.info("SKIP strategy: checking for existing products");

        // Получаем существующие productId из БД
        Set<String> existingProductIds = getExistingProductIds(productEntities, clientId);

        // Подсчитываем пропущенные
        int skippedProducts = 0;
        for (ImportableEntity entity : productEntities) {
            Product product = (Product) entity;
            if (product.getProductId() != null && existingProductIds.contains(product.getProductId())) {
                skippedProducts++;
            }
        }

        log.info("SKIP strategy: found {} existing products to skip", skippedProducts);
        result.setSkipped(skippedProducts);

        // Сохраняем только новые продукты
        List<ImportableEntity> newProducts = productEntities.stream()
                .filter(entity -> {
                    Product product = (Product) entity;
                    return product.getProductId() == null ||
                            !existingProductIds.contains(product.getProductId());
                })
                .collect(Collectors.toList());

        if (!newProducts.isEmpty()) {
            BatchSaveResult productResult = batchEntityProcessor.saveBatch(
                    newProducts, "PRODUCT", DuplicateStrategy.IGNORE);
            result.setSaved(productResult.getSaved());
            result.setFailed(productResult.getFailed());
        }

        // Обрабатываем связанные сущности
        if (holder != null && !newProducts.isEmpty()) {
            // Собираем productId новых продуктов
            Set<String> newProductIds = newProducts.stream()
                    .map(e -> ((Product) e).getProductId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Устанавливаем связи только для новых продуктов
            Map<String, Long> productIdToDbId = new HashMap<>();
            for (ImportableEntity entity : newProducts) {
                Product product = (Product) entity;
                if (product.getId() != null && product.getProductId() != null) {
                    productIdToDbId.put(product.getProductId(), product.getId());
                }
            }

            // Фильтруем связанные сущности - оставляем только те, что связаны с новыми продуктами
            Map<String, List<ImportableEntity>> filteredRelatedEntities = new HashMap<>();

            for (EntityRelationshipHolder.ImportRow row : holder.getAllRows()) {
                if (row.getProductId() != null && newProductIds.contains(row.getProductId())) {
                    Long dbId = productIdToDbId.get(row.getProductId());
                    if (dbId != null) {
                        Product productRef = new Product();
                        productRef.setId(dbId);

                        // Устанавливаем связи и добавляем в список для сохранения
                        for (ImportableEntity competitor : row.getCompetitors()) {
                            ((my.java.model.entity.Competitor) competitor).setProduct(productRef);
                            filteredRelatedEntities.computeIfAbsent("COMPETITOR", k -> new ArrayList<>())
                                    .add(competitor);
                        }

                        for (ImportableEntity region : row.getRegions()) {
                            ((my.java.model.entity.Region) region).setProduct(productRef);
                            filteredRelatedEntities.computeIfAbsent("REGION", k -> new ArrayList<>())
                                    .add(region);
                        }
                    }
                }
            }

            // Сохраняем отфильтрованные связанные сущности
            for (Map.Entry<String, List<ImportableEntity>> entry : filteredRelatedEntities.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    BatchSaveResult relatedResult = batchEntityProcessor.saveBatch(
                            entry.getValue(), entry.getKey(), DuplicateStrategy.IGNORE);
                    result.setSaved(result.getSaved() + relatedResult.getSaved());
                    result.setFailed(result.getFailed() + relatedResult.getFailed());
                }
            }

            // Подсчитываем пропущенные связанные сущности
            int skippedRelated = relatedEntities.values().stream()
                    .mapToInt(List::size).sum() -
                    filteredRelatedEntities.values().stream()
                            .mapToInt(List::size).sum();
            result.setSkipped(result.getSkipped() + skippedRelated);
        }

        log.info("SKIP strategy completed: saved {}, skipped {}", result.getSaved(), result.getSkipped());

        return result;
    }

    @Override
    public DuplicateStrategy getType() {
        return DuplicateStrategy.SKIP;
    }

    /**
     * Обработка только продуктов
     */
    private BatchSaveResult processProducts(List<ImportableEntity> entities, Long clientId) {
        Set<String> existingProductIds = getExistingProductIds(entities, clientId);

        List<ImportableEntity> newProducts = entities.stream()
                .filter(entity -> {
                    Product product = (Product) entity;
                    return product.getProductId() == null ||
                            !existingProductIds.contains(product.getProductId());
                })
                .collect(Collectors.toList());

        BatchSaveResult result = new BatchSaveResult();
        result.setSkipped(entities.size() - newProducts.size());

        if (!newProducts.isEmpty()) {
            BatchSaveResult saveResult = batchEntityProcessor.saveBatch(
                    newProducts, "PRODUCT", DuplicateStrategy.IGNORE);
            result.setSaved(saveResult.getSaved());
        }

        return result;
    }

    /**
     * Получение существующих productId из БД
     */
    private Set<String> getExistingProductIds(List<ImportableEntity> entities, Long clientId) {
        List<String> productIds = entities.stream()
                .map(e -> ((Product) e).getProductId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (productIds.isEmpty()) {
            return Collections.emptySet();
        }

        return new HashSet<>(productRepository.findExistingProductIds(clientId, productIds));
    }
}