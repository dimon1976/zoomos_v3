// src/main/java/my/java/service/file/importer/strategy/SkipDuplicatesStrategy.java
package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.service.file.importer.BatchEntityProcessor;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;
import my.java.service.file.importer.EntityRelationshipHolder;

import java.util.*;

/**
 * Стратегия пропуска дубликатов внутри файла
 * При обнаружении дубликата по productId - пропускаем всю строку
 */
@Slf4j
@RequiredArgsConstructor
public class SkipDuplicatesStrategy implements DuplicateHandlingStrategy {

    private final BatchEntityProcessor batchEntityProcessor;

    @Override
    public BatchSaveResult process(EntityRelationshipHolder holder, Long clientId) {
        log.info("=== SKIP Strategy Processing ===");
        log.info("Input: {} products, {} competitors, {} regions",
                holder.getProductsByProductId().size(),
                holder.getCompetitorsByProductId().size(),
                holder.getRegionsByProductId().size());

        BatchSaveResult result = new BatchSaveResult();

        // Находим дубликаты внутри файла по productId
        Set<String> seenProductIds = new HashSet<>();
        Set<String> duplicateProductIds = new HashSet<>();

        for (String productId : holder.getProductsByProductId().keySet()) {
            if (productId != null && !seenProductIds.add(productId)) {
                duplicateProductIds.add(productId);
                log.debug("Found duplicate productId in file: {}", productId);
            }
        }

        log.info("Found {} duplicate productIds: {}", duplicateProductIds.size(), duplicateProductIds);

        // Оставляем только уникальные записи (первое вхождение)
        List<Product> uniqueProducts = filterUniqueProducts(holder, duplicateProductIds);
        List<Competitor> uniqueCompetitors = filterUniqueCompetitors(holder, duplicateProductIds);
        List<Region> uniqueRegions = filterUniqueRegions(holder, duplicateProductIds);

        log.info("After filtering: {} unique products, {} unique competitors, {} unique regions",
                uniqueProducts.size(), uniqueCompetitors.size(), uniqueRegions.size());

        // Подсчитываем пропущенные записи
        int skippedProducts = holder.getProductsByProductId().size() - uniqueProducts.size();
        int skippedCompetitors = countSkippedCompetitors(holder, duplicateProductIds);
        int skippedRegions = countSkippedRegions(holder, duplicateProductIds);

        result.setSkipped(skippedProducts + skippedCompetitors + skippedRegions);

        log.info("Skipped {} duplicate records (products: {}, competitors: {}, regions: {})",
                result.getSkipped(), skippedProducts, skippedCompetitors, skippedRegions);

        // Сохраняем уникальные записи
        if (!uniqueProducts.isEmpty()) {
            log.info("Saving {} unique products...", uniqueProducts.size());
            BatchSaveResult productResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(uniqueProducts), "PRODUCT", DuplicateStrategy.IGNORE);
            result.setSaved(productResult.getSaved());
            log.info("Saved {} products, failed: {}", productResult.getSaved(), productResult.getFailed());
            if (!productResult.getErrors().isEmpty()) {
                log.error("Product save errors: {}", productResult.getErrors());
            }
        }

        if (!uniqueCompetitors.isEmpty()) {
            log.info("Saving {} unique competitors...", uniqueCompetitors.size());
            BatchSaveResult competitorResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(uniqueCompetitors), "COMPETITOR", DuplicateStrategy.IGNORE);
            result.setSaved(result.getSaved() + competitorResult.getSaved());
            log.info("Saved {} competitors, failed: {}", competitorResult.getSaved(), competitorResult.getFailed());
            if (!competitorResult.getErrors().isEmpty()) {
                log.error("Competitor save errors: {}", competitorResult.getErrors());
            }
        }

        if (!uniqueRegions.isEmpty()) {
            log.info("Saving {} unique regions...", uniqueRegions.size());
            BatchSaveResult regionResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(uniqueRegions), "REGION", DuplicateStrategy.IGNORE);
            result.setSaved(result.getSaved() + regionResult.getSaved());
            log.info("Saved {} regions, failed: {}", regionResult.getSaved(), regionResult.getFailed());
            if (!regionResult.getErrors().isEmpty()) {
                log.error("Region save errors: {}", regionResult.getErrors());
            }
        }

        log.info("=== SKIP Strategy Result: saved={}, skipped={}, failed={} ===",
                result.getSaved(), result.getSkipped(), result.getFailed());

        return result;
    }

    @Override
    public String getStrategyType() {
        return "SKIP";
    }

    /**
     * Фильтрует продукты, исключая дубликаты (оставляет первое вхождение)
     */
    private List<Product> filterUniqueProducts(EntityRelationshipHolder holder, Set<String> duplicateIds) {
        List<Product> uniqueProducts = new ArrayList<>();
        Set<String> addedProductIds = new HashSet<>();

        for (Product product : holder.getAllProducts()) {
            String productId = product.getProductId();
            if (productId == null || !duplicateIds.contains(productId) || addedProductIds.add(productId)) {
                uniqueProducts.add(product);
            }
        }

        return uniqueProducts;
    }

    /**
     * Фильтрует конкурентов, исключая связанных с дубликатами
     */
    private List<Competitor> filterUniqueCompetitors(EntityRelationshipHolder holder, Set<String> duplicateIds) {
        return holder.getCompetitorsExcludingProductIds(duplicateIds);
    }

    /**
     * Фильтрует регионы, исключая связанных с дубликатами
     */
    private List<Region> filterUniqueRegions(EntityRelationshipHolder holder, Set<String> duplicateIds) {
        return holder.getRegionsExcludingProductIds(duplicateIds);
    }

    private int countSkippedCompetitors(EntityRelationshipHolder holder, Set<String> duplicateIds) {
        return holder.getCompetitorsByProductId().entrySet().stream()
                .filter(e -> duplicateIds.contains(e.getKey()))
                .mapToInt(e -> e.getValue().size())
                .sum();
    }

    private int countSkippedRegions(EntityRelationshipHolder holder, Set<String> duplicateIds) {
        return holder.getRegionsByProductId().entrySet().stream()
                .filter(e -> duplicateIds.contains(e.getKey()))
                .mapToInt(e -> e.getValue().size())
                .sum();
    }
}
