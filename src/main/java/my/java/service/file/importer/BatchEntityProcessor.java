// src/main/java/my/java/service/file/importer/BatchEntityProcessor.java
package my.java.service.file.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.repository.ProductRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для пакетного сохранения сущностей в БД
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BatchEntityProcessor {

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;

    /**
     * Пакетное сохранение сущностей с обработкой дубликатов
     */
    @Transactional
    public BatchSaveResult saveBatch(List<ImportableEntity> entities,
                                     String entityType,
                                     DuplicateStrategy strategy) {

        log.debug("Saving batch of {} {} entities with strategy {}",
                entities.size(), entityType, strategy);

        switch (entityType) {
            case "PRODUCT":
                return saveProductsBatch(entities.stream()
                        .map(Product.class::cast)
                        .collect(Collectors.toList()), strategy);

            case "COMPETITOR":
                return saveCompetitorsBatch(entities.stream()
                        .map(Competitor.class::cast)
                        .collect(Collectors.toList()), strategy);

            case "REGION":
                return saveRegionsBatch(entities.stream()
                        .map(Region.class::cast)
                        .collect(Collectors.toList()), strategy);

            default:
                throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
    }

    /**
     * Пакетное сохранение продуктов
     */
    private BatchSaveResult saveProductsBatch(List<Product> products, DuplicateStrategy strategy) {
        BatchSaveResult result = new BatchSaveResult();

        if (products.isEmpty()) {
            return result;
        }

        try {
            switch (strategy) {
                case SKIP:
                    return saveProductsWithSkip(products);
                case OVERRIDE:
                    return saveProductsWithOverride(products);
                case IGNORE:
                    return saveProductsIgnoreDuplicates(products);
                default:
                    throw new IllegalArgumentException("Unknown strategy: " + strategy);
            }
        } catch (Exception e) {
            log.error("Error saving products batch: {}", e.getMessage(), e);
            result.setFailed(products.size());
            result.addError("Batch save failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Сохранение продуктов с пропуском дубликатов
     */
    private BatchSaveResult saveProductsWithSkip(List<Product> products) {
        BatchSaveResult result = new BatchSaveResult();

        // Получаем существующие productId через репозиторий
        Set<String> existingIds = getExistingProductIdsOptimized(products);

        // Разделяем на новые и дубликаты
        List<Product> newProducts = new ArrayList<>();
        for (Product product : products) {
            if (product.getProductId() != null && existingIds.contains(product.getProductId())) {
                result.incrementSkipped();
                log.debug("Skipping duplicate product: {}", product.getProductId());
            } else {
                newProducts.add(product);
            }
        }

        // Сохраняем только новые
        if (!newProducts.isEmpty()) {
            int saved = insertProductsBatch(newProducts);
            result.setSaved(saved);
        }

        return result;
    }

    /**
     * Сохранение продуктов с обновлением существующих
     */
    private BatchSaveResult saveProductsWithOverride(List<Product> products) {
        BatchSaveResult result = new BatchSaveResult();

        // Получаем существующие productId
        Set<String> existingIds = getExistingProductIdsOptimized(products);

        // Разделяем на новые и обновляемые
        List<Product> newProducts = new ArrayList<>();
        List<Product> updateProducts = new ArrayList<>();

        for (Product product : products) {
            if (product.getProductId() != null && existingIds.contains(product.getProductId())) {
                updateProducts.add(product);
            } else {
                newProducts.add(product);
            }
        }

        // Сохраняем новые
        if (!newProducts.isEmpty()) {
            int saved = insertProductsBatch(newProducts);
            result.setSaved(saved);
        }

        // Обновляем существующие
        if (!updateProducts.isEmpty()) {
            int updated = updateProductsBatch(updateProducts);
            result.setUpdated(updated);
        }

        return result;
    }

    /**
     * Сохранение продуктов без проверки дубликатов
     */
    private BatchSaveResult saveProductsIgnoreDuplicates(List<Product> products) {
        BatchSaveResult result = new BatchSaveResult();

        int saved = insertProductsBatch(products);
        result.setSaved(saved);

        return result;
    }

    /**
     * Оптимизированное получение существующих ID продуктов
     */
    private Set<String> getExistingProductIdsOptimized(List<Product> products) {
        // Группируем по clientId для оптимизации запросов
        Map<Long, List<String>> productIdsByClient = products.stream()
                .filter(p -> p.getProductId() != null && p.getClientId() != null)
                .collect(Collectors.groupingBy(
                        Product::getClientId,
                        Collectors.mapping(Product::getProductId, Collectors.toList())
                ));

        Set<String> existingIds = new HashSet<>();

        for (Map.Entry<Long, List<String>> entry : productIdsByClient.entrySet()) {
            Long clientId = entry.getKey();
            List<String> productIds = entry.getValue().stream().distinct().collect(Collectors.toList());

            if (!productIds.isEmpty()) {
                List<String> existing = productRepository.findExistingProductIds(clientId, productIds);
                existingIds.addAll(existing);
            }
        }

        return existingIds;
    }

    /**
     * Пакетная вставка продуктов
     */
    private int insertProductsBatch(List<Product> products) {
        String sql = """
            INSERT INTO products (
                client_id, data_source, file_id, product_id, product_name, product_brand,
                product_bar, product_description, product_url, product_category1,
                product_category2, product_category3, product_price, product_analog,
                product_additional1, product_additional2, product_additional3,
                product_additional4, product_additional5
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        log.debug("Inserting batch of {} products", products.size());

        // Логируем первый продукт для диагностики
        if (!products.isEmpty()) {
            Product first = products.get(0);
            log.debug("Sample product: clientId={}, productId='{}', productName='{}', productBrand='{}'",
                    first.getClientId(), first.getProductId(), first.getProductName(), first.getProductBrand());
        }

        return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) ps -> {
            int count = 0;
            for (Product product : products) {
                try {
                    setProductParameters(ps, product);
                    ps.addBatch();
                    count++;

                    if (count % 1000 == 0) {
                        int[] batchResults = ps.executeBatch();
                        log.debug("Executed batch of {} products, results: {}", 1000, batchResults.length);
                    }
                } catch (Exception e) {
                    log.error("Error setting parameters for product: {}", product.getProductId(), e);
                    throw e;
                }
            }

            if (count % 1000 != 0) {
                int[] results = ps.executeBatch();
                log.debug("Executed final batch of {} products", results.length);
            }

            log.info("Successfully inserted {} products", count);
            return count;
        });
    }

    /**
     * Пакетное обновление продуктов
     */
    private int updateProductsBatch(List<Product> products) {
        String sql = """
            UPDATE products SET 
                product_name = ?, product_brand = ?, product_bar = ?, product_description = ?,
                product_url = ?, product_category1 = ?, product_category2 = ?, product_category3 = ?,
                product_price = ?, product_analog = ?, product_additional1 = ?, product_additional2 = ?,
                product_additional3 = ?, product_additional4 = ?, product_additional5 = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE product_id = ? AND client_id = ?
            """;

        return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) ps -> {
            int count = 0;
            for (Product product : products) {
                setUpdateProductParameters(ps, product);
                ps.addBatch();
                count++;

                if (count % 1000 == 0) {
                    ps.executeBatch();
                }
            }

            int[] results = ps.executeBatch();
            return results.length;
        });
    }

    /**
     * Установка параметров для вставки продукта
     */
    private void setProductParameters(PreparedStatement ps, Product product) throws SQLException {
        log.trace("Setting parameters for product: clientId={}, productId='{}', productName='{}'",
                product.getClientId(), product.getProductId(), product.getProductName());

        ps.setLong(1, product.getClientId());
        ps.setString(2, product.getDataSource() != null ? product.getDataSource().name() : "FILE");
        ps.setObject(3, product.getFileId());
        ps.setString(4, product.getProductId());
        ps.setString(5, product.getProductName());
        ps.setString(6, product.getProductBrand());
        ps.setString(7, product.getProductBar());
        ps.setString(8, product.getProductDescription());
        ps.setString(9, product.getProductUrl());
        ps.setString(10, product.getProductCategory1());
        ps.setString(11, product.getProductCategory2());
        ps.setString(12, product.getProductCategory3());
        ps.setObject(13, product.getProductPrice());
        ps.setString(14, product.getProductAnalog());
        ps.setString(15, product.getProductAdditional1());
        ps.setString(16, product.getProductAdditional2());
        ps.setString(17, product.getProductAdditional3());
        ps.setString(18, product.getProductAdditional4());
        ps.setString(19, product.getProductAdditional5());
    }

    /**
     * Установка параметров для обновления продукта
     */
    private void setUpdateProductParameters(PreparedStatement ps, Product product) throws SQLException {
        ps.setString(1, product.getProductName());
        ps.setString(2, product.getProductBrand());
        ps.setString(3, product.getProductBar());
        ps.setString(4, product.getProductDescription());
        ps.setString(5, product.getProductUrl());
        ps.setString(6, product.getProductCategory1());
        ps.setString(7, product.getProductCategory2());
        ps.setString(8, product.getProductCategory3());
        ps.setObject(9, product.getProductPrice());
        ps.setString(10, product.getProductAnalog());
        ps.setString(11, product.getProductAdditional1());
        ps.setString(12, product.getProductAdditional2());
        ps.setString(13, product.getProductAdditional3());
        ps.setString(14, product.getProductAdditional4());
        ps.setString(15, product.getProductAdditional5());
        ps.setString(16, product.getProductId());
        ps.setLong(17, product.getClientId());
    }

    /**
     * Пакетное сохранение конкурентов
     */
    private BatchSaveResult saveCompetitorsBatch(List<Competitor> competitors, DuplicateStrategy strategy) {
        BatchSaveResult result = new BatchSaveResult();

        if (competitors.isEmpty()) {
            return result;
        }

        // Для конкурентов обычно не проверяем дубликаты, так как данные могут повторяться по времени
        String sql = """
            INSERT INTO competitor_data (
                client_id, competitor_name, competitor_price, competitor_promotional_price,
                competitor_time, competitor_date, competitor_local_date_time,
                competitor_stock_status, competitor_additional_price, competitor_commentary,
                competitor_product_name, competitor_additional, competitor_additional2,
                competitor_url, competitor_web_cache_url
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try {
            int saved = jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) ps -> {
                int count = 0;
                for (Competitor competitor : competitors) {
                    setCompetitorParameters(ps, competitor);
                    ps.addBatch();
                    count++;

                    if (count % 1000 == 0) {
                        ps.executeBatch();
                    }
                }

                int[] results = ps.executeBatch();
                return results.length;
            });

            result.setSaved(saved);
        } catch (Exception e) {
            log.error("Error saving competitors batch: {}", e.getMessage(), e);
            result.setFailed(competitors.size());
            result.addError("Competitor batch save failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Установка параметров для конкурента
     */
    private void setCompetitorParameters(PreparedStatement ps, Competitor competitor) throws SQLException {
        ps.setLong(1, competitor.getClientId());
        ps.setString(2, competitor.getCompetitorName());
        ps.setString(3, competitor.getCompetitorPrice());
        ps.setString(4, competitor.getCompetitorPromotionalPrice());
        ps.setString(5, competitor.getCompetitorTime());
        ps.setString(6, competitor.getCompetitorDate());
        ps.setObject(7, competitor.getCompetitorLocalDateTime());
        ps.setString(8, competitor.getCompetitorStockStatus());
        ps.setString(9, competitor.getCompetitorAdditionalPrice());
        ps.setString(10, competitor.getCompetitorCommentary());
        ps.setString(11, competitor.getCompetitorProductName());
        ps.setString(12, competitor.getCompetitorAdditional());
        ps.setString(13, competitor.getCompetitorAdditional2());
        ps.setString(14, competitor.getCompetitorUrl());
        ps.setString(15, competitor.getCompetitorWebCacheUrl());
    }

    /**
     * Пакетное сохранение регионов
     */
    private BatchSaveResult saveRegionsBatch(List<Region> regions, DuplicateStrategy strategy) {
        BatchSaveResult result = new BatchSaveResult();

        if (regions.isEmpty()) {
            return result;
        }

        String sql = """
            INSERT INTO region_data (
                client_id, region, region_address
            ) VALUES (?, ?, ?)
            """;

        try {
            int saved = jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) ps -> {
                int count = 0;
                for (Region region : regions) {
                    setRegionParameters(ps, region);
                    ps.addBatch();
                    count++;

                    if (count % 1000 == 0) {
                        ps.executeBatch();
                    }
                }

                int[] results = ps.executeBatch();
                return results.length;
            });

            result.setSaved(saved);
        } catch (Exception e) {
            log.error("Error saving regions batch: {}", e.getMessage(), e);
            result.setFailed(regions.size());
            result.addError("Region batch save failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Установка параметров для региона
     */
    private void setRegionParameters(PreparedStatement ps, Region region) throws SQLException {
        ps.setLong(1, region.getClientId());
        ps.setString(2, region.getRegion());
        ps.setString(3, region.getRegionAddress());
    }
}

/**
 * Стратегии обработки дубликатов
 */
enum DuplicateStrategy {
    SKIP,     // Пропускать дубликаты (записывать информацию для анализа)
    OVERRIDE, // Обновлять существующие записи
    IGNORE    // Игнорировать проверку дубликатов (записывать все)
}

/**
 * Результат пакетного сохранения
 */
class BatchSaveResult {
    private int saved = 0;
    private int updated = 0;
    private int skipped = 0;
    private int failed = 0;
    private final List<String> errors = new ArrayList<>();

    public int getSaved() { return saved; }
    public void setSaved(int saved) { this.saved = saved; }

    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }

    public int getSkipped() { return skipped; }
    public void incrementSkipped() { this.skipped++; }

    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }

    public List<String> getErrors() { return errors; }
    public void addError(String error) { errors.add(error); }

    public int getTotal() { return saved + updated + skipped + failed; }
}