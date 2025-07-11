package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.repository.ProductRepository;
import my.java.service.file.importer.BatchEntityProcessor;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.DuplicateStrategy;
import my.java.service.file.importer.EntityRelationshipHolder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Стратегия пропуска дубликатов
 * При обнаружении дубликата продукта пропускаются все связанные записи
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

    /**
     * Обработка COMBINED импорта с использованием EntityRelationshipHolder
     */
    public BatchSaveResult processCombined(List<ImportableEntity> productEntities,
                                           Map<String, List<ImportableEntity>> relatedEntities,
                                           Long clientId,
                                           EntityRelationshipHolder holder) {

        BatchSaveResult result = new BatchSaveResult();

        // Получаем существующие продукты
        Set<String> existingProductIds = getExistingProductIds(productEntities, clientId);

        // Фильтруем продукты - оставляем только новые
        List<Product> newProducts = new ArrayList<>();
        Set<String> skippedProductIds = new HashSet<>();

        for (ImportableEntity entity : productEntities) {
            Product product = (Product) entity;
            if (product.getProductId() != null && existingProductIds.contains(product.getProductId())) {
                result.incrementSkipped();
                skippedProductIds.add(product.getProductId());
                log.debug("Skipping duplicate product: clientId={}, productId={}",
                        clientId, product.getProductId());
            } else {
                newProducts.add(product);
            }
        }

        // Сохраняем новые продукты
        if (!newProducts.isEmpty()) {
            BatchSaveResult productResult = batchEntityProcessor.saveBatch(
                    new ArrayList<>(newProducts), "PRODUCT", DuplicateStrategy.IGNORE);
            result.setSaved(productResult.getSaved());
        }

        // Фильтруем связанные сущности
        if (holder != null) {
            // Используем holder для точной фильтрации
            List<Competitor> competitorsToSave = holder.getCompetitorsExcludingProductIds(skippedProductIds);
            List<Region> regionsToSave = holder.getRegionsExcludingProductIds(skippedProductIds);

            if (!competitorsToSave.isEmpty()) {
                BatchSaveResult competitorResult = batchEntityProcessor.saveBatch(
                        new ArrayList<>(competitorsToSave), "COMPETITOR", DuplicateStrategy.IGNORE);
                result.setSaved(result.getSaved() + competitorResult.getSaved());
            }

            if (!regionsToSave.isEmpty()) {
                BatchSaveResult regionResult = batchEntityProcessor.saveBatch(
                        new ArrayList<>(regionsToSave), "REGION", DuplicateStrategy.IGNORE);
                result.setSaved(result.getSaved() + regionResult.getSaved());
            }

            // Подсчитываем пропущенные
            int skippedCompetitors = holder.getCompetitorsByProductId().entrySet().stream()
                    .filter(e -> skippedProductIds.contains(e.getKey()))
                    .mapToInt(e -> e.getValue().size())
                    .sum();

            int skippedRegions = holder.getRegionsByProductId().entrySet().stream()
                    .filter(e -> skippedProductIds.contains(e.getKey()))
                    .mapToInt(e -> e.getValue().size())
                    .sum();

            result.addSkipped(skippedCompetitors + skippedRegions);

        } else {
            // Fallback для случаев без holder
            for (Map.Entry<String, List<ImportableEntity>> entry : relatedEntities.entrySet()) {
                String entityType = entry.getKey();
                List<ImportableEntity> entities = entry.getValue();

                if (!entities.isEmpty()) {
                    // Без holder сохраняем все связанные сущности
                    BatchSaveResult relatedResult = batchEntityProcessor.saveBatch(
                            entities, entityType, DuplicateStrategy.IGNORE);
                    result.setSaved(result.getSaved() + relatedResult.getSaved());
                }
            }
        }

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

    /**
     * Фильтрация связанных сущностей - исключаем те, что связаны с пропущенными продуктами
     */
    private List<ImportableEntity> filterRelatedEntities(List<ImportableEntity> entities,
                                                         Set<String> skippedProductIds) {
        return entities.stream()
                .filter(entity -> {
                    // Получаем productId из связанной сущности
                    String productId = getProductIdFromEntity(entity);
                    return productId == null || !skippedProductIds.contains(productId);
                })
                .collect(Collectors.toList());
    }

    /**
     * Получение productId из связанной сущности
     * Использует информацию, сохраненную в EntityRelationshipHolder
     */
    private String getProductIdFromEntity(ImportableEntity entity) {
        // В контексте использования EntityRelationshipHolder,
        // productId должен быть доступен через связь, установленную в holder
        // Для Competitor и Region это будет productId, с которым они были связаны

        // Это временное решение - в реальности productId должен передаваться
        // через контекст обработки или сохраняться в метаданных сущности
        return null;
    }
}