package my.java.service.file.importer;

import lombok.Getter;
import my.java.model.entity.Competitor;
import my.java.model.entity.Product;
import my.java.model.entity.Region;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Класс для хранения связей между сущностями при импорте
 * Заменяет хак с временным хранением productId в полях других сущностей
 */
@Getter
public class EntityRelationshipHolder {

    private final Map<String, Product> productsByProductId = new HashMap<>();
    private final Map<String, List<Competitor>> competitorsByProductId = new HashMap<>();
    private final Map<String, List<Region>> regionsByProductId = new HashMap<>();

    // Дополнительные карты для хранения обратных связей
    private final Map<Competitor, String> competitorToProductId = new HashMap<>();
    private final Map<Region, String> regionToProductId = new HashMap<>();

    /**
     * Добавить продукт
     */
    public void addProduct(Product product) {
        if (product.getProductId() != null) {
            productsByProductId.put(product.getProductId(), product);
        }
    }

    /**
     * Добавить конкурента с привязкой к productId
     */
    public void addCompetitor(String productId, Competitor competitor) {
        if (productId != null) {
            competitorsByProductId.computeIfAbsent(productId, k -> new ArrayList<>()).add(competitor);
            competitorToProductId.put(competitor, productId);
        }
    }

    /**
     * Добавить регион с привязкой к productId
     */
    public void addRegion(String productId, Region region) {
        if (productId != null) {
            regionsByProductId.computeIfAbsent(productId, k -> new ArrayList<>()).add(region);
            regionToProductId.put(region, productId);
        }
    }

    /**
     * Получить все продукты
     */
    public List<Product> getAllProducts() {
        return new ArrayList<>(productsByProductId.values());
    }

    /**
     * Получить всех конкурентов для продукта
     */
    public List<Competitor> getCompetitorsForProduct(String productId) {
        return competitorsByProductId.getOrDefault(productId, Collections.emptyList());
    }

    /**
     * Получить все регионы для продукта
     */
    public List<Region> getRegionsForProduct(String productId) {
        return regionsByProductId.getOrDefault(productId, Collections.emptyList());
    }


    /**
     * Установить связи с Product после сохранения в БД
     *
     * @param productIdToDbId маппинг productId -> ID в БД
     */
    public void establishDatabaseRelationships(Map<String, Long> productIdToDbId) {
        // Устанавливаем связи для конкурентов
        for (Map.Entry<String, List<Competitor>> entry : competitorsByProductId.entrySet()) {
            String productId = entry.getKey();
            Long dbId = productIdToDbId.get(productId);

            if (dbId != null) {
                Product product = new Product();
                product.setId(dbId);

                for (Competitor competitor : entry.getValue()) {
                    competitor.setProduct(product);
                }
            }
        }

        // Устанавливаем связи для регионов
        for (Map.Entry<String, List<Region>> entry : regionsByProductId.entrySet()) {
            String productId = entry.getKey();
            Long dbId = productIdToDbId.get(productId);

            if (dbId != null) {
                Product product = new Product();
                product.setId(dbId);

                for (Region region : entry.getValue()) {
                    region.setProduct(product);
                }
            }
        }
    }

    /**
     * Получить количество сущностей
     */
    public int getTotalEntitiesCount() {
        return productsByProductId.size() +
                competitorsByProductId.values().stream().mapToInt(List::size).sum() +
                regionsByProductId.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Получить все конкуренты без указанных productId
     */
    public List<Competitor> getCompetitorsExcludingProductIds(Set<String> excludedProductIds) {
        return competitorToProductId.entrySet().stream()
                .filter(entry -> !excludedProductIds.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Получить все регионы без указанных productId
     */
    public List<Region> getRegionsExcludingProductIds(Set<String> excludedProductIds) {
        return regionToProductId.entrySet().stream()
                .filter(entry -> !excludedProductIds.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}