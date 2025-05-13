// src/main/java/my/java/service/file/entity/CompositeEntityService.java
package my.java.service.file.entity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.FileOperation;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.service.entity.competitor.CompetitorService;
import my.java.service.entity.product.ProductService;
import my.java.service.entity.region.RegionService;
import my.java.service.file.options.FileReadingOptions;
import my.java.service.file.transformer.ValueTransformerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для работы с составными сущностями при импорте и экспорте
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompositeEntityService {

    private final ProductService productService;
    private final RegionService regionService;
    private final CompetitorService competitorService;
    private final ValueTransformerFactory transformerFactory;

    // Кэш для хранения созданных сущностей Product
    private final Map<String, Product> productCache = new ConcurrentHashMap<>();

    /**
     * Обработка строки данных из файла для импорта в составные сущности
     */
    @Transactional
    public List<ImportableEntity> processRowWithOptions(
            Map<String, String> data,
            Map<String, String> mappedFields,
            Long clientId,
            FileReadingOptions options,
            FileOperation operation) { // Добавляем параметр operation

        // Подготавливаем карты данных для каждой сущности
        Map<String, Map<String, String>> entityData = prepareEntityData(data, mappedFields);
        List<ImportableEntity> resultEntities = new ArrayList<>();
        String duplicateHandling = options.getDuplicateHandling();

        // Обрабатываем основную сущность (Product)
        if (entityData.containsKey("product")) {
            Product product = processProductDataWithOptions(
                    entityData.get("product"),
                    clientId,
                    duplicateHandling,
                    operation.getId()); // Передаем ID операции

            if (product != null) {
                resultEntities.add(product);

                // Обработка связанных сущностей в одном цикле
                for (Map.Entry<String, Map<String, String>> entry : entityData.entrySet()) {
                    String entityType = entry.getKey();
                    Map<String, String> entityDataMap = entry.getValue();

                    if ("product".equals(entityType)) {
                        continue; // Основную сущность уже обработали
                    }

                    ImportableEntity relatedEntity = processRelatedEntity(
                            entityType,
                            entityDataMap,
                            product,
                            clientId,
                            operation.getId()); // Передаем ID операции

                    if (relatedEntity != null) {
                        resultEntities.add(relatedEntity);
                    }
                }
            }
        }

        return resultEntities;
    }


    /**
     * Обрабатывает связанную сущность в зависимости от ее типа
     */
    private ImportableEntity processRelatedEntity(
            String entityType,
            Map<String, String> data,
            Product product,
            Long clientId,
            Long operationId) {

        // Проверяем наличие данных для сущности
        if (!hasEntityData(data, entityType)) {
            return null;
        }

        try {
            switch (entityType) {
                case "region":
                    Region region = new Region();
                    region.setTransformerFactory(transformerFactory);
                    region.setClientId(clientId);
                    region.setProduct(product);
                    region.setImportOperationId(operationId);

                    if (!region.fillFromMap(data)) {
                        return null;
                    }

                    // Проверяем существование и обновляем/создаем
                    Optional<Region> existingRegion = regionService.findByRegionAndProductId(
                            region.getRegion(), product.getId());

                    if (existingRegion.isPresent()) {
                        Region existing = existingRegion.get();
                        existing.setRegionAddress(region.getRegionAddress());
                        return regionService.saveRegion(existing);
                    }

                    return regionService.saveRegion(region);

                case "competitor":
                    Competitor competitor = new Competitor();
                    competitor.setTransformerFactory(transformerFactory);
                    competitor.setClientId(clientId);
                    competitor.setProduct(product);
                    competitor.setImportOperationId(operationId);

                    if (!competitor.fillFromMap(data)) {
                        return null;
                    }

                    // Проверяем существование и обновляем/создаем
                    Optional<Competitor> existingCompetitor = competitorService.findByCompetitorNameAndProductId(
                            competitor.getCompetitorName(), product.getId());

                    if (existingCompetitor.isPresent()) {
                        Competitor existing = existingCompetitor.get();
                        updateCompetitorFields(existing, competitor);
                        return competitorService.saveCompetitor(existing);
                    }

                    return competitorService.saveCompetitor(competitor);

                default:
                    log.warn("Неизвестный тип связанной сущности: {}", entityType);
                    return null;
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке связанной сущности {}: {}", entityType, e.getMessage());
            return null;
        }
    }

    /**
     * Обработка данных для сущности Product с учетом стратегии обработки дубликатов
     */
    // Обновляем методы обработки сущностей
    private Product processProductDataWithOptions(
            Map<String, String> data,
            Long clientId,
            String duplicateHandling,
            Long operationId) { // Добавляем параметр operationId

        try {
            Product product = new Product();
            product.setTransformerFactory(transformerFactory);
            product.setClientId(clientId);
            product.setImportOperationId(operationId); // Устанавливаем ID операции

            // Заполняем поля продукта
            if (!product.fillFromMap(data)) {
                return null;
            }

            // Проверяем существование продукта по его внешнему идентификатору
            if (product.getProductId() != null) {
                // Проверяем кэш
                String cacheKey = clientId + "_" + product.getProductId();
                Product cachedProduct = productCache.get(cacheKey);

                if (cachedProduct != null) {
                    // При работе с кэшем, сохраняем операцию импорта
                    cachedProduct.setImportOperationId(operationId);

                    // Обрабатываем по выбранной стратегии
                    switch (duplicateHandling) {
                        case "error":
                            throw new FileOperationException("Найден дубликат продукта: " + product.getProductId());
                        case "skip":
                            return cachedProduct;
                        case "update":
                            return updateExistingProduct(cachedProduct, product);
                    }
                }

                // Проверяем БД
                Optional<Product> existingProduct = productService.findByProductIdAndClientId(
                        product.getProductId(), clientId);

                if (existingProduct.isPresent()) {
                    Product existing = existingProduct.get();
                    existing.setImportOperationId(operationId); // Обновляем ID операции

                    // Обрабатываем по выбранной стратегии
                    switch (duplicateHandling) {
                        case "error":
                            throw new FileOperationException("Найден дубликат продукта: " + product.getProductId());
                        case "skip":
                            productCache.put(cacheKey, existing);
                            return existing;
                        case "update":
                            Product updatedProduct = updateExistingProduct(existing, product);
                            productCache.put(cacheKey, updatedProduct);
                            return updatedProduct;
                    }
                }
            }

            // Возвращаем продукт
            return productService.saveProduct(product);

        } catch (Exception e) {
            log.error("Ошибка при обработке данных Product: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Обновляет поля существующего продукта
     */
    private Product updateExistingProduct(Product existing, Product newData) {
        // Обновляем поля существующего продукта
        existing.setProductName(newData.getProductName());
        existing.setProductBrand(newData.getProductBrand());
        existing.setProductBar(newData.getProductBar());
        existing.setProductDescription(newData.getProductDescription());
        existing.setProductUrl(newData.getProductUrl());
        existing.setProductCategory1(newData.getProductCategory1());
        existing.setProductCategory2(newData.getProductCategory2());
        existing.setProductCategory3(newData.getProductCategory3());
        existing.setProductPrice(newData.getProductPrice());
        existing.setProductAnalog(newData.getProductAnalog());
        existing.setProductAdditional1(newData.getProductAdditional1());
        existing.setProductAdditional2(newData.getProductAdditional2());
        existing.setProductAdditional3(newData.getProductAdditional3());
        existing.setProductAdditional4(newData.getProductAdditional4());
        existing.setProductAdditional5(newData.getProductAdditional5());

        return productService.saveProduct(existing);
    }

    /**
     * Подготовка данных для каждой сущности из общей карты данных
     */
    private Map<String, Map<String, String>> prepareEntityData(
            Map<String, String> data,
            Map<String, String> mappedFields) {

        Map<String, Map<String, String>> result = new HashMap<>();

        // Добавляем данные с уже имеющимися префиксами
        data.entrySet().stream()
                .filter(e -> e.getKey().contains(".") && !e.getValue().trim().isEmpty())
                .forEach(e -> {
                    String[] parts = e.getKey().split("\\.", 2);
                    if (parts.length == 2) {
                        result.computeIfAbsent(parts[0], k -> new HashMap<>())
                                .put(parts[1], e.getValue());
                    }
                });

        // Добавляем данные с префиксами из маппинга
        if (mappedFields != null) {
            mappedFields.entrySet().stream()
                    .filter(mapping -> data.containsKey(mapping.getKey()) &&
                            !data.get(mapping.getKey()).trim().isEmpty() &&
                            mapping.getValue().contains("."))
                    .forEach(mapping -> {
                        String sourceField = mapping.getKey();
                        String targetField = mapping.getValue();
                        String[] parts = targetField.split("\\.", 2);

                        if (parts.length == 2) {
                            result.computeIfAbsent(parts[0], k -> new HashMap<>())
                                    .put(parts[1], data.get(sourceField));
                        }
                    });
        }

        return result;
    }

    /**
     * Обработка данных для сущности Region
     */
    private Region processRegionData(Map<String, String> data, Product product, Long clientId) {
        try {
            // Проверяем наличие данных для региона
            boolean hasRegionData = hasEntityData(data, "region");
            if (!hasRegionData) return null;

            Region region = new Region();
            region.setTransformerFactory(transformerFactory);
            region.setClientId(clientId);
            region.setProduct(product);

            // Заполняем поля региона
            if (!region.fillFromMap(data)) return null;

            // Проверяем существование региона
            Optional<Region> existingRegion = regionService.findByRegionAndProductId(
                    region.getRegion(), product.getId());

            if (existingRegion.isPresent()) {
                Region existing = existingRegion.get();
                existing.setRegionAddress(region.getRegionAddress());
                return regionService.saveRegion(existing);
            }

            return regionService.saveRegion(region);
        } catch (Exception e) {
            log.error("Ошибка при обработке данных Region: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Обработка данных для сущности Competitor
     */
    private Competitor processCompetitorData(Map<String, String> data, Product product, Long clientId) {
        try {
            // Проверяем наличие данных для конкурента
            boolean hasCompetitorData = hasEntityData(data, "competitor");
            if (!hasCompetitorData) return null;

            Competitor competitor = new Competitor();
            competitor.setTransformerFactory(transformerFactory);
            competitor.setClientId(clientId);
            competitor.setProduct(product);

            // Заполняем поля конкурента
            if (!competitor.fillFromMap(data)) return null;

            // Проверяем существование конкурента
            Optional<Competitor> existingCompetitor = competitorService.findByCompetitorNameAndProductId(
                    competitor.getCompetitorName(), product.getId());

            if (existingCompetitor.isPresent()) {
                Competitor existing = existingCompetitor.get();
                updateCompetitorFields(existing, competitor);
                return competitorService.saveCompetitor(existing);
            }

            return competitorService.saveCompetitor(competitor);
        } catch (Exception e) {
            log.error("Ошибка при обработке данных Competitor: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Проверяет наличие данных для указанной сущности
     */
    private boolean hasEntityData(Map<String, String> data, String entityPrefix) {
        for (String key : data.keySet()) {
            if (key.startsWith(entityPrefix)) {
                String value = data.get(key);
                if (value != null && !value.trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Обновление полей существующего конкурента
     */
    private void updateCompetitorFields(Competitor target, Competitor source) {
        target.setCompetitorPrice(source.getCompetitorPrice());
        target.setCompetitorPromotionalPrice(source.getCompetitorPromotionalPrice());
        target.setCompetitorTime(source.getCompetitorTime());
        target.setCompetitorDate(source.getCompetitorDate());
        target.setCompetitorLocalDateTime(source.getCompetitorLocalDateTime());
        target.setCompetitorStockStatus(source.getCompetitorStockStatus());
        target.setCompetitorAdditionalPrice(source.getCompetitorAdditionalPrice());
        target.setCompetitorCommentary(source.getCompetitorCommentary());
        target.setCompetitorProductName(source.getCompetitorProductName());
        target.setCompetitorAdditional(source.getCompetitorAdditional());
        target.setCompetitorAdditional2(source.getCompetitorAdditional2());
        target.setCompetitorUrl(source.getCompetitorUrl());
        target.setCompetitorWebCacheUrl(source.getCompetitorWebCacheUrl());
    }

    /**
     * Экспорт данных из составных сущностей
     */
    public Map<String, String> exportEntityData(String mainEntityType, Long id, List<String> fields) {
        if (!"product".equals(mainEntityType)) {
            return Collections.emptyMap();
        }

        // Получаем продукт
        Optional<Product> productOpt = productService.findById(id);
        if (productOpt.isEmpty()) {
            return Collections.emptyMap();
        }

        Product product = productOpt.get();
        Map<String, String> result = new HashMap<>();

        // Обрабатываем каждое поле
        for (String field : fields) {
            String[] parts = field.split("\\.", 2);
            if (parts.length != 2) continue;

            String entityType = parts[0];
            String fieldName = parts[1];

            // В зависимости от типа сущности, получаем значение поля
            switch (entityType) {
                case "product":
                    addEntityField(result, product, field, fieldName);
                    break;
                case "region":
                    if (!product.getRegionList().isEmpty()) {
                        addEntityField(result, product.getRegionList().get(0), field, fieldName);
                    }
                    break;
                case "competitor":
                    if (!product.getCompetitorList().isEmpty()) {
                        addEntityField(result, product.getCompetitorList().get(0), field, fieldName);
                    }
                    break;
            }
        }

        return result;
    }

    /**
     * Добавление поля сущности в результат экспорта
     */
    private void addEntityField(Map<String, String> result, Object entity, String fullField, String fieldName) {
        try {
            // Используем рефлексию для получения значения поля
            java.lang.reflect.Method getter = entity.getClass().getMethod(
                    "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
            Object value = getter.invoke(entity);

            // Преобразуем значение в строку
            result.put(fullField, value != null ? transformerFactory.toString(value, null) : "");
        } catch (Exception e) {
            log.warn("Ошибка при получении поля {} сущности: {}", fieldName, e.getMessage());
        }
    }

    /**
     * Создание новой сущности по типу
     */
    public ImportableEntity createEntityInstance(String entityType) {
        ImportableEntity entity = null;

        switch (entityType) {
            case "product" -> entity = new Product();
            case "region" -> entity = new Region();
            case "competitor" -> entity = new Competitor();
            default -> {
                log.warn("Неизвестный тип сущности: {}", entityType);
                return null;
            }
        }

        // Устанавливаем трансформер
        try {
            entity.getClass().getMethod("setTransformerFactory", ValueTransformerFactory.class)
                    .invoke(entity, transformerFactory);
        } catch (Exception e) {
            log.warn("Не удалось установить трансформер для сущности типа {}", entityType);
        }

        return entity;
    }

    /**
     * Очистка кэша продуктов
     */
    public void clearCache() {
        productCache.clear();
    }
}