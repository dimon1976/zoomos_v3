// src/main/java/my/java/service/file/importer/strategy/IgnoreDuplicatesStrategy.java
package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.Region;
import my.java.service.file.importer.BatchEntityProcessor;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;
import my.java.service.file.importer.EntityRelationshipHolder;

import java.util.ArrayList;

/**
 * Стратегия игнорирования дубликатов
 * Сохраняет все записи без проверки дубликатов
 */
@Slf4j
@RequiredArgsConstructor
public class IgnoreDuplicatesStrategy implements DuplicateHandlingStrategy {

    private final BatchEntityProcessor batchEntityProcessor;

    @Override
    public BatchSaveResult process(EntityRelationshipHolder holder, Long clientId) {
        log.debug("Processing with IGNORE strategy - saving all {} products",
                holder.getProductsByProductId().size());

        BatchSaveResult result = new BatchSaveResult();

        // Сохраняем все продукты
        if (!holder.getAllProducts().isEmpty()) {
            BatchSaveResult productResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(holder.getAllProducts()), "PRODUCT", DuplicateStrategy.IGNORE);
            result.setSaved(productResult.getSaved());
        }

        // Сохраняем всех конкурентов
        if (!holder.getCompetitorsByProductId().isEmpty()) {
            var allCompetitors = holder.getCompetitorsByProductId().values().stream()
                    .flatMap(java.util.List::stream)
                    .toList();

            BatchSaveResult competitorResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(allCompetitors), "COMPETITOR", DuplicateStrategy.IGNORE);
            result.setSaved(result.getSaved() + competitorResult.getSaved());
        }

        // Сохраняем все регионы
        if (!holder.getRegionsByProductId().isEmpty()) {
            var allRegions = holder.getRegionsByProductId().values().stream()
                    .flatMap(java.util.List::stream)
                    .toList();

            BatchSaveResult regionResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(allRegions), "REGION", DuplicateStrategy.IGNORE);
            result.setSaved(result.getSaved() + regionResult.getSaved());
        }

        return result;
    }

    @Override
    public String getStrategyType() {
        return "IGNORE";
    }
}