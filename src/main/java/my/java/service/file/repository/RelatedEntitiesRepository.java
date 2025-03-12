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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        // Сохраняем продукты
        List<Product> products = groupedEntities.getOrDefault(Product.class, new ArrayList<>()).stream()
                .map(e -> (Product) e)
                .collect(Collectors.toList());

        if (!products.isEmpty()) {
            savedCount += productService.saveProducts(products);
            log.debug("Сохранено {} продуктов", products.size());
        }

        // Сохраняем регионы
        List<RegionData> regionDataList = groupedEntities.getOrDefault(RegionData.class, new ArrayList<>()).stream()
                .map(e -> (RegionData) e)
                .collect(Collectors.toList());

        if (!regionDataList.isEmpty()) {
            savedCount += regionDataService.saveRegionDataList(regionDataList);
            log.debug("Сохранено {} данных регионов", regionDataList.size());
        }

        // Сохраняем конкурентов
        List<CompetitorData> competitorDataList = groupedEntities.getOrDefault(CompetitorData.class, new ArrayList<>()).stream()
                .map(e -> (CompetitorData) e)
                .collect(Collectors.toList());

        if (!competitorDataList.isEmpty()) {
            savedCount += competitorDataService.saveCompetitorDataList(competitorDataList);
            log.debug("Сохранено {} данных конкурентов", competitorDataList.size());
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

        // Объединяем все сущности из всех групп
        List<ImportableEntity> allEntities = entitySets.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        return saveRelatedEntities(allEntities);
    }
}