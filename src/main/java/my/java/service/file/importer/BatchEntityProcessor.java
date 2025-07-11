package my.java.service.file.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.repository.ProductRepository;
import org.apache.commons.lang3.tuple.Pair;
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
 * Рефакторинг для улучшения структуры и упрощения кода
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

        if (entities.isEmpty()) {
            return new BatchSaveResult();
        }

        try {
            // Делегируем обработку соответствующему процессору
            EntityProcessor processor = createProcessor(entityType);
            return processor.processBatch(entities, strategy);

        } catch (Exception e) {
            log.error("Error saving {} batch: {}", entityType, e.getMessage(), e);
            BatchSaveResult result = new BatchSaveResult();
            result.setFailed(entities.size());
            result.addError("Batch save failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Создание процессора для конкретного типа сущности
     */
    private EntityProcessor createProcessor(String entityType) {
        return switch (entityType) {
            case "PRODUCT" -> new ProductProcessor();
            case "COMPETITOR" -> new CompetitorProcessor();
            case "REGION" -> new RegionProcessor();
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    /**
     * Базовый интерфейс для процессоров сущностей
     */
    private interface EntityProcessor {
        BatchSaveResult processBatch(List<ImportableEntity> entities, DuplicateStrategy strategy);
    }

    /**
     * Процессор для продуктов
     */
    private class ProductProcessor implements EntityProcessor {

        @Override
        public BatchSaveResult processBatch(List<ImportableEntity> entities, DuplicateStrategy strategy) {
            List<Product> products = entities.stream()
                    .map(e -> (Product) e)
                    .collect(Collectors.toList());

            return switch (strategy) {
                case SKIP -> processWithSkip(products);
                case OVERRIDE -> processWithOverride(products);
                case IGNORE -> processIgnoreDuplicates(products);
            };
        }

        private BatchSaveResult processWithSkip(List<Product> products) {
            BatchSaveResult result = new BatchSaveResult();

            // Получаем существующие пары (clientId, productId)
            Set<Pair<Long, String>> existingPairs = getExistingProductPairs(products);

            // Фильтруем только новые продукты
            List<Product> newProducts = products.stream()
                    .filter(p -> p.getProductId() == null ||
                            !existingPairs.contains(Pair.of(p.getClientId(), p.getProductId())))
                    .collect(Collectors.toList());

            int skippedCount = products.size() - newProducts.size();
            result.setSkipped(skippedCount);

            if (skippedCount > 0) {
                log.debug("Skipped {} duplicate products", skippedCount);
            }

            if (!newProducts.isEmpty()) {
                int saved = insertProductsBatch(newProducts);
                result.setSaved(saved);
            }

            return result;
        }

        private BatchSaveResult processWithOverride(List<Product> products) {
            BatchSaveResult result = new BatchSaveResult();

            // Получаем карту существующих продуктов для обновления
            Map<Pair<Long, String>, Long> existingProductIds = getExistingProductIdsMap(products);

            List<Product> toInsert = new ArrayList<>();
            List<Product> toUpdate = new ArrayList<>();

            for (Product product : products) {
                if (product.getProductId() != null && product.getClientId() != null) {
                    Pair<Long, String> key = Pair.of(product.getClientId(), product.getProductId());
                    Long existingId = existingProductIds.get(key);

                    if (existingId != null) {
                        product.setId(existingId);
                        toUpdate.add(product);
                    } else {
                        toInsert.add(product);
                    }
                } else {
                    toInsert.add(product);
                }
            }

            if (!toInsert.isEmpty()) {
                int saved = insertProductsBatch(toInsert);
                result.setSaved(saved);
            }

            if (!toUpdate.isEmpty()) {
                int updated = updateProductsBatch(toUpdate);
                result.setUpdated(updated);
            }

            return result;
        }

        private BatchSaveResult processIgnoreDuplicates(List<Product> products) {
            BatchSaveResult result = new BatchSaveResult();
            int saved = insertProductsBatch(products);
            result.setSaved(saved);
            return result;
        }

        private Set<Pair<Long, String>> getExistingProductPairs(List<Product> products) {
            Map<Long, List<String>> productIdsByClient = products.stream()
                    .filter(p -> p.getProductId() != null && p.getClientId() != null)
                    .collect(Collectors.groupingBy(
                            Product::getClientId,
                            Collectors.mapping(Product::getProductId, Collectors.toList())
                    ));

            Set<Pair<Long, String>> existingPairs = new HashSet<>();

            for (Map.Entry<Long, List<String>> entry : productIdsByClient.entrySet()) {
                Long clientId = entry.getKey();
                List<String> productIds = entry.getValue().stream()
                        .distinct()
                        .collect(Collectors.toList());

                if (!productIds.isEmpty()) {
                    List<Object[]> existing = productRepository.findExistingProductPairs(clientId, productIds);
                    for (Object[] row : existing) {
                        Long foundClientId = (Long) row[0];
                        String foundProductId = (String) row[1];
                        existingPairs.add(Pair.of(foundClientId, foundProductId));
                    }
                }
            }

            return existingPairs;
        }

        private Map<Pair<Long, String>, Long> getExistingProductIdsMap(List<Product> products) {
            Map<Pair<Long, String>, Long> result = new HashMap<>();

            Map<Long, List<String>> productIdsByClient = products.stream()
                    .filter(p -> p.getProductId() != null && p.getClientId() != null)
                    .collect(Collectors.groupingBy(
                            Product::getClientId,
                            Collectors.mapping(Product::getProductId, Collectors.toList())
                    ));

            for (Map.Entry<Long, List<String>> entry : productIdsByClient.entrySet()) {
                Long clientId = entry.getKey();
                List<String> productIds = entry.getValue().stream()
                        .distinct()
                        .collect(Collectors.toList());

                for (String productId : productIds) {
                    productRepository.findByProductIdAndClientId(productId, clientId)
                            .ifPresent(p -> result.put(Pair.of(clientId, productId), p.getId()));
                }
            }

            return result;
        }
    }

    /**
     * Процессор для конкурентов
     */
    private class CompetitorProcessor implements EntityProcessor {

        @Override
        public BatchSaveResult processBatch(List<ImportableEntity> entities, DuplicateStrategy strategy) {
            List<Competitor> competitors = entities.stream()
                    .map(e -> (Competitor) e)
                    .collect(Collectors.toList());

            // Для конкурентов пока всегда используем INSERT
            // так как дубликаты определяются по связи с продуктом
            return insertCompetitorsBatch(competitors);
        }

        private BatchSaveResult insertCompetitorsBatch(List<Competitor> competitors) {
            BatchSaveResult result = new BatchSaveResult();

            String sql = """
                    INSERT INTO competitor_data (
                        client_id, product_id, competitor_name, competitor_price, 
                        competitor_promotional_price, competitor_time, competitor_date, 
                        competitor_local_date_time, competitor_stock_status, 
                        competitor_additional_price, competitor_commentary,
                        competitor_product_name, competitor_additional, competitor_additional2,
                        competitor_url, competitor_web_cache_url, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """;

            try {
                int saved = executeBatch(sql, competitors, this::setCompetitorParameters);
                result.setSaved(saved);
                log.info("Successfully saved {} competitors", saved);
            } catch (Exception e) {
                log.error("Error saving competitors batch", e);
                result.setFailed(competitors.size());
                result.addError("Competitor batch save failed: " + e.getMessage());
            }

            return result;
        }

        private void setCompetitorParameters(PreparedStatement ps, Competitor competitor) throws SQLException {
            ps.setLong(1, competitor.getClientId());
            ps.setObject(2, competitor.getProduct() != null ? competitor.getProduct().getId() : null);
            ps.setString(3, competitor.getCompetitorName());
            ps.setString(4, competitor.getCompetitorPrice());
            ps.setString(5, competitor.getCompetitorPromotionalPrice());
            ps.setString(6, competitor.getCompetitorTime());
            ps.setString(7, competitor.getCompetitorDate());
            ps.setObject(8, competitor.getCompetitorLocalDateTime());
            ps.setString(9, competitor.getCompetitorStockStatus());
            ps.setString(10, competitor.getCompetitorAdditionalPrice());
            ps.setString(11, competitor.getCompetitorCommentary());
            ps.setString(12, competitor.getCompetitorProductName());
            ps.setString(13, competitor.getCompetitorAdditional());
            ps.setString(14, competitor.getCompetitorAdditional2());
            ps.setString(15, competitor.getCompetitorUrl());
            ps.setString(16, competitor.getCompetitorWebCacheUrl());
        }
    }

    /**
     * Процессор для регионов
     */
    private class RegionProcessor implements EntityProcessor {

        @Override
        public BatchSaveResult processBatch(List<ImportableEntity> entities, DuplicateStrategy strategy) {
            List<Region> regions = entities.stream()
                    .map(e -> (Region) e)
                    .collect(Collectors.toList());

            // Для регионов пока всегда используем INSERT
            return insertRegionsBatch(regions);
        }

        private BatchSaveResult insertRegionsBatch(List<Region> regions) {
            BatchSaveResult result = new BatchSaveResult();

            String sql = """
                    INSERT INTO region_data (
                        client_id, product_id, region, region_address, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """;

            try {
                int saved = executeBatch(sql, regions, this::setRegionParameters);
                result.setSaved(saved);
                log.info("Successfully saved {} regions", saved);
            } catch (Exception e) {
                log.error("Error saving regions batch", e);
                result.setFailed(regions.size());
                result.addError("Region batch save failed: " + e.getMessage());
            }

            return result;
        }

        private void setRegionParameters(PreparedStatement ps, Region region) throws SQLException {
            ps.setLong(1, region.getClientId());
            ps.setObject(2, region.getProduct() != null ? region.getProduct().getId() : null);
            ps.setString(3, region.getRegion());
            ps.setString(4, region.getRegionAddress());
        }
    }

    /**
     * Пакетная вставка продуктов
     */
    private int insertProductsBatch(List<Product> products) {
        String sql = """
                INSERT INTO products (
                    client_id, data_source, product_id, product_name, product_brand,
                    product_bar, product_description, product_url, product_category1,
                    product_category2, product_category3, product_price, product_analog,
                    product_additional1, product_additional2, product_additional3,
                    product_additional4, product_additional5, operation_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        return executeBatch(sql, products, this::setProductInsertParameters);
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
                WHERE id = ?
                """;

        return executeBatch(sql, products, this::setProductUpdateParameters);
    }

    /**
     * Универсальный метод выполнения батча
     */
    private <T> int executeBatch(String sql, List<T> items, ParameterSetter<T> parameterSetter) {
        log.debug("Executing batch for {} items", items.size());

        return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) ps -> {
            int count = 0;
            for (T item : items) {
                try {
                    parameterSetter.setParameters(ps, item);
                    ps.addBatch();
                    count++;

                    if (count % 1000 == 0) {
                        int[] batchResults = ps.executeBatch();
                        log.debug("Executed batch of {} items", batchResults.length);
                    }
                } catch (Exception e) {
                    log.error("Error setting parameters for item: {}", item, e);
                    throw e;
                }
            }

            if (count % 1000 != 0) {
                int[] results = ps.executeBatch();
                log.debug("Executed final batch of {} items", results.length);
            }

            return count;
        });
    }

    /**
     * Установка параметров для вставки продукта
     */
    private void setProductInsertParameters(PreparedStatement ps, Product product) throws SQLException {
        ps.setLong(1, product.getClientId());
        ps.setString(2, product.getDataSource() != null ? product.getDataSource().name() : "FILE");
        ps.setString(3, product.getProductId());
        ps.setString(4, product.getProductName());
        ps.setString(5, product.getProductBrand());
        ps.setString(6, product.getProductBar());
        ps.setString(7, product.getProductDescription());
        ps.setString(8, product.getProductUrl());
        ps.setString(9, product.getProductCategory1());
        ps.setString(10, product.getProductCategory2());
        ps.setString(11, product.getProductCategory3());
        ps.setObject(12, product.getProductPrice());
        ps.setString(13, product.getProductAnalog());
        ps.setString(14, product.getProductAdditional1());
        ps.setString(15, product.getProductAdditional2());
        ps.setString(16, product.getProductAdditional3());
        ps.setString(17, product.getProductAdditional4());
        ps.setString(18, product.getProductAdditional5());
        ps.setObject(19, product.getOperationId());
    }

    /**
     * Установка параметров для обновления продукта
     */
    private void setProductUpdateParameters(PreparedStatement ps, Product product) throws SQLException {
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
        ps.setLong(16, product.getId());
    }

    /**
     * Функциональный интерфейс для установки параметров
     */
    @FunctionalInterface
    private interface ParameterSetter<T> {
        void setParameters(PreparedStatement ps, T item) throws SQLException;
    }
}