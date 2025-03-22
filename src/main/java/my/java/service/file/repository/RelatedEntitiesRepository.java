package my.java.service.file.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.CompetitorData;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.service.competitor.CompetitorDataService;
import my.java.service.product.ProductService;
import my.java.service.region.RegionDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Репозиторий для работы с группами связанных сущностей.
 * Обеспечивает атомарное сохранение и корректную установку связей.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelatedEntitiesRepository {

    private final ProductService productService;
    private final RegionDataService regionDataService;
    private final CompetitorDataService competitorDataService;

    /**
     * Сохраняет группу связанных сущностей в БД.
     *
     * @param entities список сущностей для сохранения
     * @return количество сохраненных сущностей
     */
    @Transactional
    public int saveRelatedEntities(List<ImportableEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        // Группируем сущности по типу
        Map<Class<?>, List<ImportableEntity>> groupedEntities = groupEntitiesByType(entities);

        // Если нет продуктов, сохраняем регионы и конкурентов отдельно
        List<Product> products = getEntitiesByType(groupedEntities, Product.class);
        if (products.isEmpty()) {
            return saveEntitiesWithoutProducts(groupedEntities);
        }

        // Сохраняем продукты и связанные с ними сущности
        return saveProductsWithRelatedEntities(products, groupedEntities);
    }

    /**
     * Сохраняет группу сущностей, полученных от строителя.
     *
     * @param entitySets список групп сущностей от строителей
     * @return количество сохраненных сущностей
     */
    @Transactional
    public int saveEntitySets(List<List<ImportableEntity>> entitySets) {
        if (entitySets == null || entitySets.isEmpty()) {
            return 0;
        }

        // Обрабатываем каждую группу сущностей по отдельности
        return entitySets.stream()
                .mapToInt(this::saveRelatedEntities)
                .sum();
    }

    /**
     * Группирует сущности по их типу
     *
     * @param entities список сущностей
     * @return карта сущностей, сгруппированных по типу
     */
    private Map<Class<?>, List<ImportableEntity>> groupEntitiesByType(List<ImportableEntity> entities) {
        return entities.stream()
                .collect(Collectors.groupingBy(ImportableEntity::getClass));
    }

    /**
     * Извлекает сущности указанного типа из сгруппированных сущностей
     *
     * @param groupedEntities сгруппированные сущности
     * @param type тип сущностей
     * @return список сущностей указанного типа
     */
    @SuppressWarnings("unchecked")
    private <T extends ImportableEntity> List<T> getEntitiesByType(
            Map<Class<?>, List<ImportableEntity>> groupedEntities,
            Class<T> type) {
        return groupedEntities.getOrDefault(type, new ArrayList<>())
                .stream()
                .map(e -> (T) e)
                .collect(Collectors.toList());
    }

    /**
     * Сохраняет сущности без привязки к продуктам
     *
     * @param groupedEntities сгруппированные сущности
     * @return количество сохраненных сущностей
     */
    private int saveEntitiesWithoutProducts(Map<Class<?>, List<ImportableEntity>> groupedEntities) {
        int savedCount = 0;

        // Сохраняем регионы
        List<RegionData> regionDataList = getEntitiesByType(groupedEntities, RegionData.class);
        if (!regionDataList.isEmpty()) {
            savedCount += regionDataService.saveRegionDataList(regionDataList);
            log.debug("Сохранено {} данных регионов", regionDataList.size());
        }

        // Сохраняем конкурентов
        List<CompetitorData> competitorDataList = getEntitiesByType(groupedEntities, CompetitorData.class);
        if (!competitorDataList.isEmpty()) {
            savedCount += competitorDataService.saveCompetitorDataList(competitorDataList);
            log.debug("Сохранено {} данных конкурентов", competitorDataList.size());
        }

        return savedCount;
    }

    /**
     * Сохраняет продукты и связанные с ними сущности
     *
     * @param products список продуктов
     * @param groupedEntities сгруппированные сущности
     * @return количество сохраненных сущностей
     */
    private int saveProductsWithRelatedEntities(List<Product> products,
                                                Map<Class<?>, List<ImportableEntity>> groupedEntities) {
        int savedCount = 0;

        // Сохраняем все продукты
        List<Product> savedProducts = saveProducts(products);
        savedCount += savedProducts.size();

        // Создаем карту соответствия для отслеживания связей между сущностями
        Map<Product, List<ImportableEntity>> productRelations = createProductRelationsMap(savedProducts);

        // Обрабатываем и сохраняем связанные регионы
        List<RegionData> regionDataList = getEntitiesByType(groupedEntities, RegionData.class);
        if (!regionDataList.isEmpty()) {
            savedCount += processAndSaveRelatedEntities(
                    regionDataList,
                    savedProducts,
                    productRelations,
                    RegionData::getProduct,
                    RegionData::setProduct,
                    regionDataService::saveRegionDataList,
                    "регионов"
            );
        }

        // Обрабатываем и сохраняем связанных конкурентов
        List<CompetitorData> competitorDataList = getEntitiesByType(groupedEntities, CompetitorData.class);
        if (!competitorDataList.isEmpty()) {
            savedCount += processAndSaveRelatedEntities(
                    competitorDataList,
                    savedProducts,
                    productRelations,
                    CompetitorData::getProduct,
                    CompetitorData::setProduct,
                    competitorDataService::saveCompetitorDataList,
                    "конкурентов"
            );
        }

        // Выводим информацию о связях для отладки
        logProductRelations(productRelations);

        return savedCount;
    }

    /**
     * Сохраняет продукты и возвращает список сохраненных продуктов
     *
     * @param products список продуктов для сохранения
     * @return список сохраненных продуктов
     */
    private List<Product> saveProducts(List<Product> products) {
        List<Product> savedProducts = new ArrayList<>();
        for (Product product : products) {
            Product savedProduct = productService.saveProduct(product);
            savedProducts.add(savedProduct);
        }
        log.debug("Сохранено {} продуктов", savedProducts.size());
        return savedProducts;
    }

    /**
     * Создает карту связей продуктов с другими сущностями
     *
     * @param savedProducts список сохраненных продуктов
     * @return карта связей продуктов
     */
    private Map<Product, List<ImportableEntity>> createProductRelationsMap(List<Product> savedProducts) {
        Map<Product, List<ImportableEntity>> productRelations = new HashMap<>();
        for (Product product : savedProducts) {
            productRelations.put(product, new ArrayList<>());
        }
        return productRelations;
    }

    /**
     * Обрабатывает и сохраняет связанные с продуктами сущности
     *
     * @param entities список сущностей
     * @param savedProducts список сохраненных продуктов
     * @param productRelations карта связей продуктов
     * @param getProductFunc функция получения продукта из сущности
     * @param setProductFunc функция установки продукта в сущность
     * @param saveFunc функция сохранения списка сущностей
     * @param entityType строковое представление типа сущности для логирования
     * @return количество сохраненных сущностей
     */
    private <T extends ImportableEntity> int processAndSaveRelatedEntities(
            List<T> entities,
            List<Product> savedProducts,
            Map<Product, List<ImportableEntity>> productRelations,
            Function<T, Product> getProductFunc,
            java.util.function.BiConsumer<T, Product> setProductFunc,
            Function<List<T>, Integer> saveFunc,
            String entityType) {

        // Ищем соответствующий продукт для каждой сущности
        for (T entity : entities) {
            Product originalProduct = getProductFunc.apply(entity);
            if (originalProduct != null) {
                // Находим сохраненный продукт
                Optional<Product> savedProductOpt = findProductByOriginal(savedProducts, originalProduct);
                if (savedProductOpt.isPresent()) {
                    Product savedProduct = savedProductOpt.get();
                    setProductFunc.accept(entity, savedProduct);

                    // Добавляем сущность в список связанных сущностей продукта
                    productRelations.get(savedProduct).add(entity);
                }
            }
        }

        // Сохраняем сущности
        int savedCount = saveFunc.apply(entities);
        log.debug("Сохранено {} данных {}", savedCount, entityType);
        return savedCount;
    }

    /**
     * Логирует информацию о связях продуктов
     *
     * @param productRelations карта связей продуктов
     */
    private void logProductRelations(Map<Product, List<ImportableEntity>> productRelations) {
        if (!productRelations.isEmpty()) {
            for (Map.Entry<Product, List<ImportableEntity>> entry : productRelations.entrySet()) {
                Product product = entry.getKey();
                List<ImportableEntity> relatedEntities = entry.getValue();

                log.debug("Продукт #{} '{}' связан с {} сущностями",
                        product.getId(),
                        product.getProductName(),
                        relatedEntities.size());
            }
        }
    }

    /**
     * Находит сохраненный продукт, соответствующий оригинальному.
     *
     * @param savedProducts список сохраненных продуктов
     * @param originalProduct оригинальный продукт
     * @return сохраненный продукт или пустой Optional
     */
    private Optional<Product> findProductByOriginal(List<Product> savedProducts, Product originalProduct) {
        // Сначала пробуем найти по ID
        if (originalProduct.getId() != null) {
            return savedProducts.stream()
                    .filter(p -> originalProduct.getId().equals(p.getId()))
                    .findFirst();
        }

        // Затем пробуем найти по productId
        if (originalProduct.getProductId() != null) {
            return savedProducts.stream()
                    .filter(p -> originalProduct.getProductId().equals(p.getProductId()))
                    .findFirst();
        }

        // Наконец, пробуем найти по имени
        if (originalProduct.getProductName() != null) {
            return savedProducts.stream()
                    .filter(p -> originalProduct.getProductName().equals(p.getProductName()))
                    .findFirst();
        }

        // Если не удалось найти подходящий продукт
        return Optional.empty();
    }
}