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

        int savedCount = 0;

        // Группируем сущности по типу
        Map<Class<?>, List<ImportableEntity>> groupedEntities = entities.stream()
                .collect(Collectors.groupingBy(ImportableEntity::getClass));

        // Создаем карту соответствия для отслеживания связей между сущностями
        Map<Product, List<ImportableEntity>> productRelations = new HashMap<>();

        // Сначала обрабатываем и сохраняем продукты
        List<Product> products = groupedEntities.getOrDefault(Product.class, new ArrayList<>()).stream()
                .map(e -> (Product) e)
                .collect(Collectors.toList());

        if (!products.isEmpty()) {
            // Сохраняем все продукты
            List<Product> savedProducts = new ArrayList<>();
            for (Product product : products) {
                Product savedProduct = productService.saveProduct(product);
                savedProducts.add(savedProduct);

                // Инициализируем список связанных сущностей
                productRelations.put(savedProduct, new ArrayList<>());
            }

            savedCount += savedProducts.size();
            log.debug("Сохранено {} продуктов", savedProducts.size());

            // Обрабатываем связанные регионы
            List<RegionData> regionDataList = groupedEntities.getOrDefault(RegionData.class, new ArrayList<>()).stream()
                    .map(e -> (RegionData) e)
                    .collect(Collectors.toList());

            // Ищем соответствующий продукт для каждого региона
            for (RegionData regionData : regionDataList) {
                Product originalProduct = regionData.getProduct();
                if (originalProduct != null) {
                    // Находим сохраненный продукт
                    Optional<Product> savedProductOpt = findProductByOriginal(savedProducts, originalProduct);
                    if (savedProductOpt.isPresent()) {
                        Product savedProduct = savedProductOpt.get();
                        regionData.setProduct(savedProduct);

                        // Добавляем регион в список связанных сущностей продукта
                        productRelations.get(savedProduct).add(regionData);
                    }
                }
            }

            // Сохраняем регионы
            if (!regionDataList.isEmpty()) {
                savedCount += regionDataService.saveRegionDataList(regionDataList);
                log.debug("Сохранено {} данных регионов", regionDataList.size());
            }

            // Обрабатываем связанных конкурентов
            List<CompetitorData> competitorDataList = groupedEntities.getOrDefault(CompetitorData.class, new ArrayList<>()).stream()
                    .map(e -> (CompetitorData) e)
                    .collect(Collectors.toList());

            // Ищем соответствующий продукт для каждого конкурента
            for (CompetitorData competitorData : competitorDataList) {
                Product originalProduct = competitorData.getProduct();
                if (originalProduct != null) {
                    // Находим сохраненный продукт
                    Optional<Product> savedProductOpt = findProductByOriginal(savedProducts, originalProduct);
                    if (savedProductOpt.isPresent()) {
                        Product savedProduct = savedProductOpt.get();
                        competitorData.setProduct(savedProduct);

                        // Добавляем конкурента в список связанных сущностей продукта
                        productRelations.get(savedProduct).add(competitorData);
                    }
                }
            }

            // Сохраняем конкурентов
            if (!competitorDataList.isEmpty()) {
                savedCount += competitorDataService.saveCompetitorDataList(competitorDataList);
                log.debug("Сохранено {} данных конкурентов", competitorDataList.size());
            }
        } else {
            // Если нет продуктов, сохраняем регионы и конкурентов отдельно
            List<RegionData> regionDataList = groupedEntities.getOrDefault(RegionData.class, new ArrayList<>()).stream()
                    .map(e -> (RegionData) e)
                    .collect(Collectors.toList());

            if (!regionDataList.isEmpty()) {
                savedCount += regionDataService.saveRegionDataList(regionDataList);
                log.debug("Сохранено {} данных регионов", regionDataList.size());
            }

            List<CompetitorData> competitorDataList = groupedEntities.getOrDefault(CompetitorData.class, new ArrayList<>()).stream()
                    .map(e -> (CompetitorData) e)
                    .collect(Collectors.toList());

            if (!competitorDataList.isEmpty()) {
                savedCount += competitorDataService.saveCompetitorDataList(competitorDataList);
                log.debug("Сохранено {} данных конкурентов", competitorDataList.size());
            }
        }

        // Выводим информацию о связях для отладки
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

        return savedCount;
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

        int totalSaved = 0;

        // Обрабатываем каждую группу сущностей по отдельности
        for (List<ImportableEntity> entitySet : entitySets) {
            totalSaved += saveRelatedEntities(entitySet);
        }

        return totalSaved;
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