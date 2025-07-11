// src/main/java/my/java/service/file/importer/strategy/OverrideDuplicatesStrategy.java
package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.repository.CompetitorRepository;
import my.java.repository.ProductRepository;
import my.java.repository.RegionRepository;
import my.java.service.file.importer.BatchEntityProcessor;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;
import my.java.service.file.importer.EntityRelationshipHolder;

import java.util.*;

/**
 * Стратегия перезаписи дубликатов
 * При дубликатах внутри файла - берем последнее значение
 * Для существующих в БД - полностью перезаписываем включая связанные сущности
 */
@Slf4j
@RequiredArgsConstructor
public class OverrideDuplicatesStrategy implements DuplicateHandlingStrategy {

    private final BatchEntityProcessor batchEntityProcessor;
    private final ProductRepository productRepository;
    private final CompetitorRepository competitorRepository;
    private final RegionRepository regionRepository;

    @Override
    public BatchSaveResult process(EntityRelationshipHolder holder, Long clientId) {
        log.debug("Processing with OVERRIDE strategy for {} products", holder.getProductsByProductId().size());

        BatchSaveResult result = new BatchSaveResult();

        // Убираем дубликаты внутри файла, оставляя последнее значение
        EntityRelationshipHolder deduplicatedHolder = removeDuplicatesKeepLast(holder);

        // Получаем продукты для проверки существования в БД
        List<Product> products = deduplicatedHolder.getAllProducts();
        Map<String, Long> existingProductIds = getExistingProductIds(products, clientId);

        // Разделяем на новые и обновляемые
        List<Product> newProducts = new ArrayList<>();
        List<Product> updatedProducts = new ArrayList<>();

        for (Product product : products) {
            Long existingId = existingProductIds.get(product.getProductId());
            if (existingId != null) {
                product.setId(existingId);
                updatedProducts.add(product);
            } else {
                newProducts.add(product);
            }
        }

        // Для обновляемых продуктов удаляем старые связанные записи
        if (!updatedProducts.isEmpty()) {
            deleteOldRelatedEntities(updatedProducts, clientId);
        }

        // Сохраняем все продукты
        if (!newProducts.isEmpty()) {
            BatchSaveResult newResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(newProducts), "PRODUCT", DuplicateStrategy.IGNORE);
            result.setSaved(newResult.getSaved());
        }

        if (!updatedProducts.isEmpty()) {
            BatchSaveResult updateResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(updatedProducts), "PRODUCT", DuplicateStrategy.OVERRIDE);
            result.setUpdated(updateResult.getUpdated());
        }

        // Сохраняем связанные сущности
        List<Competitor> competitors = getAllCompetitors(deduplicatedHolder);
        List<Region> regions = getAllRegions(deduplicatedHolder);

        if (!competitors.isEmpty()) {
            BatchSaveResult competitorResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(competitors), "COMPETITOR", DuplicateStrategy.IGNORE);
            result.setSaved(result.getSaved() + competitorResult.getSaved());
        }

        if (!regions.isEmpty()) {
            BatchSaveResult regionResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(regions), "REGION", DuplicateStrategy.IGNORE);
            result.setSaved(result.getSaved() + regionResult.getSaved());
        }

        return result;
    }

    @Override
    public String getStrategyType() {
        return "OVERRIDE";
    }

    /**
     * Убирает дубликаты внутри файла, оставляя последнее значение
     */
    private EntityRelationshipHolder removeDuplicatesKeepLast(EntityRelationshipHolder original) {
        EntityRelationshipHolder deduplicated = new EntityRelationshipHolder();

        // Для каждого productId берем последний Product
        Map<String, Product> lastProducts = new LinkedHashMap<>(); // LinkedHashMap сохраняет порядок
        for (Product product : original.getAllProducts()) {
            if (product.getProductId() != null) {
                lastProducts.put(product.getProductId(), product);
            }
        }

        // Добавляем уникальные продукты в новый holder
        for (Product product : lastProducts.values()) {
            deduplicated.addProduct(product);

            // Добавляем связанные сущности только для оставшихся продуктов
            String productId = product.getProductId();
            original.getCompetitorsForProduct(productId).forEach(c ->
                    deduplicated.addCompetitor(productId, c));
            original.getRegionsForProduct(productId).forEach(r ->
                    deduplicated.addRegion(productId, r));
        }

        return deduplicated;
    }

    /**
     * Получает существующие продукты из БД
     */
    private Map<String, Long> getExistingProductIds(List<Product> products, Long clientId) {
        Map<String, Long> existing = new HashMap<>();

        for (Product product : products) {
            if (product.getProductId() != null) {
                productRepository.findByProductIdAndClientId(product.getProductId(), clientId)
                        .ifPresent(p -> existing.put(product.getProductId(), p.getId()));
            }
        }

        return existing;
    }

    /**
     * Удаляет старые связанные записи для обновляемых продуктов
     */
    private void deleteOldRelatedEntities(List<Product> updatedProducts, Long clientId) {
        List<Long> productDbIds = updatedProducts.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .toList();

        if (!productDbIds.isEmpty()) {
            log.debug("Deleting old related entities for {} products", productDbIds.size());
            competitorRepository.deleteByProductIdIn(productDbIds);
            regionRepository.deleteByProductIdIn(productDbIds);
        }
    }

    private List<Competitor> getAllCompetitors(EntityRelationshipHolder holder) {
        return holder.getCompetitorsByProductId().values().stream()
                .flatMap(List::stream)
                .toList();
    }

    private List<Region> getAllRegions(EntityRelationshipHolder holder) {
        return holder.getRegionsByProductId().values().stream()
                .flatMap(List::stream)
                .toList();
    }
}
