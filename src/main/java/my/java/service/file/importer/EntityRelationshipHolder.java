package my.java.service.file.importer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.Product;
import my.java.model.entity.Region;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Класс для хранения связей между сущностями при импорте.
 * Поддерживает дубликаты продуктов для стратегии IGNORE
 */
@Slf4j
@Getter
public class EntityRelationshipHolder {

    // Структура для хранения строки данных
    @Getter
    public static class ImportRow {
        private final Product product;
        private final List<Competitor> competitors = new ArrayList<>();
        private final List<Region> regions = new ArrayList<>();
        private final String productId;

        public ImportRow(Product product, String productId) {
            this.product = product;
            this.productId = productId;
        }

        public void addCompetitor(Competitor competitor) {
            competitors.add(competitor);
        }

        public void addRegion(Region region) {
            regions.add(region);
        }
    }

    // Хранение всех строк импорта (поддерживает дубликаты)
    private final List<ImportRow> importRows = new ArrayList<>();

    // Индекс для быстрого поиска по productId (для стратегий SKIP и OVERRIDE)
    private final Map<String, List<ImportRow>> rowsByProductId = new HashMap<>();

    // Текущая обрабатываемая строка
    private ImportRow currentRow = null;

    /**
     * Начать новую строку импорта
     */
    public void startNewRow(String productId) {
        log.debug("Starting new row with productId: {}", productId);
        currentRow = null;
        if (productId != null) {
            // Для каждой строки создаем новый ImportRow
            currentRow = new ImportRow(null, productId);
            log.debug("Created new ImportRow for productId: {}", productId);
        }
    }

    /**
     * Добавить продукт в текущую строку
     */
    public void addProduct(Product product) {
        log.debug("Adding product with productId: {}, product.id: {}",
                product != null ? product.getProductId() : "null",
                product != null ? product.getId() : "null");

        if (currentRow == null) {
            // Если строка не была начата, создаем новую
            String productId = product.getProductId();
            currentRow = new ImportRow(product, productId);
            log.debug("Created new ImportRow with product, productId: {}", productId);
        } else {
            // Создаем новую строку с продуктом, сохраняя связанные сущности
            List<Competitor> tempCompetitors = new ArrayList<>(currentRow.getCompetitors());
            List<Region> tempRegions = new ArrayList<>(currentRow.getRegions());

            currentRow = new ImportRow(product, currentRow.getProductId());

            // Восстанавливаем связанные сущности
            tempCompetitors.forEach(currentRow::addCompetitor);
            tempRegions.forEach(currentRow::addRegion);

            log.debug("Replaced product in current row, kept {} competitors and {} regions",
                    tempCompetitors.size(), tempRegions.size());
        }
    }

    /**
     * Добавить конкурента в текущую строку
     */
    public void addCompetitor(String productId, Competitor competitor) {
        log.debug("Adding competitor for productId: {}, competitor.name: {}",
                productId, competitor != null ? competitor.getCompetitorName() : "null");

        if (currentRow == null) {
            currentRow = new ImportRow(null, productId);
            log.debug("Created new ImportRow for competitor, productId: {}", productId);
        }
        currentRow.addCompetitor(competitor);
        log.debug("Added competitor to current row, total competitors: {}", currentRow.getCompetitors().size());
    }

    /**
     * Добавить регион в текущую строку
     */
    public void addRegion(String productId, Region region) {
        log.debug("Adding region for productId: {}, region.name: {}",
                productId, region != null ? region.getRegion() : "null");

        if (currentRow == null) {
            currentRow = new ImportRow(null, productId);
            log.debug("Created new ImportRow for region, productId: {}", productId);
        }
        currentRow.addRegion(region);
        log.debug("Added region to current row, total regions: {}", currentRow.getRegions().size());
    }

    /**
     * Завершить текущую строку и добавить в общий список
     */
    public void finishCurrentRow() {
        if (currentRow != null) {
            log.debug("Finishing current row with identifier: {}, has product: {}, competitors: {}, regions: {}",
                    currentRow.getProductId(),
                    currentRow.getProduct() != null,
                    currentRow.getCompetitors().size(),
                    currentRow.getRegions().size());

            // Для SINGLE импорта строка может не содержать продукт
            // Проверяем, есть ли хоть какие-то сущности в строке
            boolean hasAnyEntities = currentRow.getProduct() != null ||
                    !currentRow.getCompetitors().isEmpty() ||
                    !currentRow.getRegions().isEmpty();

            if (hasAnyEntities) {
                importRows.add(currentRow);
                log.debug("Added row to importRows, total rows: {}", importRows.size());

                // Добавляем в индекс для быстрого поиска
                String identifier = currentRow.getProductId(); // это может быть и row_N для SINGLE
                if (identifier != null) {
                    rowsByProductId.computeIfAbsent(identifier, k -> new ArrayList<>()).add(currentRow);
                    log.debug("Added row to index for identifier: {}, total rows for this identifier: {}",
                            identifier, rowsByProductId.get(identifier).size());
                }
            } else {
                log.warn("Current row has no entities, skipping. Identifier: {}", currentRow.getProductId());
            }

            currentRow = null;
        } else {
            log.debug("No current row to finish");
        }
    }

    /**
     * Установить связи с Product после сохранения в БД
     */
    public void establishDatabaseRelationships(Map<String, Long> productIdToDbId) {
        log.info("Establishing database relationships for {} productIds", productIdToDbId.size());
        log.debug("ProductId to DB ID mapping: {}", productIdToDbId);

        int establishedCompetitorLinks = 0;
        int establishedRegionLinks = 0;

        for (ImportRow row : importRows) {
            String productId = row.getProductId();
            Long dbId = productIdToDbId.get(productId);

            log.debug("Processing row with productId: {}, dbId: {}", productId, dbId);

            if (dbId != null) {
                // Создаем объект Product только с ID для установки связи
                Product productRef = new Product();
                productRef.setId(dbId);

                // Устанавливаем связи для конкурентов
                for (Competitor competitor : row.getCompetitors()) {
                    log.debug("Setting product reference (id={}) for competitor: {}",
                            dbId, competitor.getCompetitorName());
                    competitor.setProduct(productRef);
                    establishedCompetitorLinks++;
                }

                // Устанавливаем связи для регионов
                for (Region region : row.getRegions()) {
                    log.debug("Setting product reference (id={}) for region: {}",
                            dbId, region.getRegion());
                    region.setProduct(productRef);
                    establishedRegionLinks++;
                }
            } else {
                log.warn("No DB ID found for productId: {}, skipping {} competitors and {} regions",
                        productId, row.getCompetitors().size(), row.getRegions().size());
            }
        }

        log.info("Established {} competitor links and {} region links",
                establishedCompetitorLinks, establishedRegionLinks);
    }

    /**
     * Получить все строки импорта
     */
    public List<ImportRow> getAllRows() {
        log.debug("Getting all rows, count: {}", importRows.size());
        return new ArrayList<>(importRows);
    }

    /**
     * Получить все уникальные продукты (для SKIP стратегии)
     */
    public List<Product> getUniqueProducts() {
        Map<String, Product> uniqueProducts = new LinkedHashMap<>();
        for (ImportRow row : importRows) {
            if (row.getProduct() != null && row.getProductId() != null) {
                uniqueProducts.putIfAbsent(row.getProductId(), row.getProduct());
            }
        }
        log.debug("Found {} unique products from {} total rows", uniqueProducts.size(), importRows.size());
        return new ArrayList<>(uniqueProducts.values());
    }

    /**
     * Получить все продукты (включая дубликаты для IGNORE стратегии)
     */
    public List<Product> getAllProducts() {
        List<Product> allProducts = importRows.stream()
                .map(ImportRow::getProduct)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        log.debug("Getting all products, count: {}", allProducts.size());
        return allProducts;
    }

    /**
     * Получить все конкуренты для продукта
     */
    public List<Competitor> getCompetitorsForProduct(String productId) {
        List<Competitor> competitors = rowsByProductId.getOrDefault(productId, Collections.emptyList()).stream()
                .flatMap(row -> row.getCompetitors().stream())
                .collect(Collectors.toList());
        log.debug("Found {} competitors for productId: {}", competitors.size(), productId);
        return competitors;
    }

    /**
     * Получить все регионы для продукта
     */
    public List<Region> getRegionsForProduct(String productId) {
        List<Region> regions = rowsByProductId.getOrDefault(productId, Collections.emptyList()).stream()
                .flatMap(row -> row.getRegions().stream())
                .collect(Collectors.toList());
        log.debug("Found {} regions for productId: {}", regions.size(), productId);
        return regions;
    }

    /**
     * Получить маппинг конкурентов по productId
     */
    public Map<String, List<Competitor>> getCompetitorsByProductId() {
        Map<String, List<Competitor>> result = new HashMap<>();
        for (Map.Entry<String, List<ImportRow>> entry : rowsByProductId.entrySet()) {
            List<Competitor> competitors = entry.getValue().stream()
                    .flatMap(row -> row.getCompetitors().stream())
                    .collect(Collectors.toList());
            if (!competitors.isEmpty()) {
                result.put(entry.getKey(), competitors);
            }
        }
        log.debug("Competitors by productId mapping size: {}", result.size());
        return result;
    }

    /**
     * Получить маппинг регионов по productId
     */
    public Map<String, List<Region>> getRegionsByProductId() {
        Map<String, List<Region>> result = new HashMap<>();
        for (Map.Entry<String, List<ImportRow>> entry : rowsByProductId.entrySet()) {
            List<Region> regions = entry.getValue().stream()
                    .flatMap(row -> row.getRegions().stream())
                    .collect(Collectors.toList());
            if (!regions.isEmpty()) {
                result.put(entry.getKey(), regions);
            }
        }
        log.debug("Regions by productId mapping size: {}", result.size());
        return result;
    }

    /**
     * Получить маппинг продуктов по productId
     */
    public Map<String, Product> getProductsByProductId() {
        Map<String, Product> result = new HashMap<>();
        for (ImportRow row : importRows) {
            if (row.getProduct() != null && row.getProductId() != null) {
                result.put(row.getProductId(), row.getProduct());
            }
        }
        log.debug("Products by productId mapping size: {}", result.size());
        return result;
    }

    /**
     * Получить общее количество сущностей
     */
    public int getTotalEntitiesCount() {
        int count = 0;
        for (ImportRow row : importRows) {
            if (row.getProduct() != null) count++;
            count += row.getCompetitors().size();
            count += row.getRegions().size();
        }
        log.debug("Total entities count: {}", count);
        return count;
    }
}