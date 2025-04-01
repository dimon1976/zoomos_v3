// src/main/java/my/java/service/file/entity/EntitySaverFactoryImpl.java
package my.java.service.file.entity;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.service.entity.competitor.CompetitorService;
import my.java.service.entity.product.ProductService;
import my.java.service.entity.region.RegionService;
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
            RegionService regionService,
            CompetitorService competitorService) {

        // Регистрируем обработчики для стандартных типов сущностей
        registerSaver("product", entities -> {
            List<Product> products = entities.stream()
                    .filter(e -> e instanceof Product)
                    .map(e -> (Product) e)
                    .toList();
            return productService.saveProducts(products);
        });

        registerSaver("regiondata", entities -> {
            List<Region> regionList = entities.stream()
                    .filter(e -> e instanceof Region)
                    .map(e -> (Region) e)
                    .toList();
            return regionService.saveRegionList(regionList);
        });

        registerSaver("competitordata", entities -> {
            List<Competitor> competitorList = entities.stream()
                    .filter(e -> e instanceof Competitor)
                    .map(e -> (Competitor) e)
                    .toList();
            return competitorService.saveCompetitorList(competitorList);
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