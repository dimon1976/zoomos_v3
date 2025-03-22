package my.java.service.file.entity;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.CompetitorData;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.service.competitor.CompetitorDataService;
import my.java.service.file.repository.RelatedEntitiesRepository;
import my.java.service.product.ProductService;
import my.java.service.region.RegionDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class EntitySaverFactoryImpl implements EntitySaverFactory {

    private final Map<String, Function<List<ImportableEntity>, Integer>> savers = new HashMap<>();

    @Autowired
    public EntitySaverFactoryImpl(
            ProductService productService,
            RegionDataService regionDataService,
            CompetitorDataService competitorDataService,
            RelatedEntitiesRepository relatedEntitiesRepository) {

        // Регистрируем обработчики для стандартных типов сущностей
        registerSaver("product", entities -> {
            List<Product> products = entities.stream()
                    .filter(e -> e instanceof Product)
                    .map(e -> (Product) e)
                    .toList();
            return productService.saveProducts(products);
        });

        registerSaver("regiondata", entities -> {
            List<RegionData> regionDataList = entities.stream()
                    .filter(e -> e instanceof RegionData)
                    .map(e -> (RegionData) e)
                    .toList();
            return regionDataService.saveRegionDataList(regionDataList);
        });

        registerSaver("competitordata", entities -> {
            List<CompetitorData> competitorDataList = entities.stream()
                    .filter(e -> e instanceof CompetitorData)
                    .map(e -> (CompetitorData) e)
                    .toList();
            return competitorDataService.saveCompetitorDataList(competitorDataList);
        });

        // Добавляем обработчик для связанных сущностей
        registerSaver("product_with_related", entities -> {
            log.info("Сохранение связанных сущностей, всего: {}", entities.size());
            return relatedEntitiesRepository.saveRelatedEntities(entities);
        });
    }

    @Override
    public Function<List<ImportableEntity>, Integer> getSaver(String entityType) {
        if (entityType == null) {
            throw new IllegalArgumentException("Тип сущности не может быть null");
        }

        String type = entityType.toLowerCase();
        if (!savers.containsKey(type)) {
            log.warn("Не найден обработчик для типа сущности: {}", type);
            return entities -> 0; // Возвращаем функцию, которая не делает ничего
        }

        return savers.get(type);
    }

    @Override
    public void registerSaver(String entityType, Function<List<ImportableEntity>, Integer> saverFunction) {
        if (entityType == null || saverFunction == null) {
            throw new IllegalArgumentException("Тип сущности и функция сохранения не могут быть null");
        }

        String type = entityType.toLowerCase();
        log.debug("Регистрация обработчика для типа сущности: {}", type);
        savers.put(type, saverFunction);
    }

    @Override
    public boolean hasSaver(String entityType) {
        if (entityType == null) {
            return false;
        }

        return savers.containsKey(entityType.toLowerCase());
    }
}