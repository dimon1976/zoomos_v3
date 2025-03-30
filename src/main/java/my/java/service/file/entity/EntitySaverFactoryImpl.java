package my.java.service.file.entity;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
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
import java.util.stream.Collectors;

/**
 * Реализация фабрики для получения компонентов, сохраняющих различные типы сущностей
 */
@Component
@Slf4j
public class EntitySaverFactoryImpl implements EntitySaverFactory {

    private final Map<String, Function<List<ImportableEntity>, Integer>> savers = new HashMap<>();

    private final ProductService productService;
    private final RegionDataService regionDataService;
    private final CompetitorDataService competitorDataService;
    private final RelatedEntitiesRepository relatedEntitiesRepository;

    @Autowired
    public EntitySaverFactoryImpl(
            ProductService productService,
            RegionDataService regionDataService,
            CompetitorDataService competitorDataService,
            RelatedEntitiesRepository relatedEntitiesRepository) {
        this.productService = productService;
        this.regionDataService = regionDataService;
        this.competitorDataService = competitorDataService;
        this.relatedEntitiesRepository = relatedEntitiesRepository;
    }

    /**
     * Инициализирует фабрику стандартными обработчиками
     */
    @PostConstruct
    public void initialize() {
        registerStandardSavers();
    }

    /**
     * Регистрирует стандартные обработчики
     */
    private void registerStandardSavers() {
        // Регистрируем обработчик для продуктов
        registerSaver("product", entities -> saveProducts(entities));

        // Регистрируем обработчик для регионов
        registerSaver("regiondata", entities -> saveRegionData(entities));

        // Регистрируем обработчик для конкурентов
        registerSaver("competitordata", entities -> saveCompetitorData(entities));

        // Добавляем обработчик для связанных сущностей
        registerSaver("product_with_related", entities -> relatedEntitiesRepository.saveRelatedEntities(entities));
    }

    /**
     * Сохраняет продукты
     *
     * @param entities список сущностей
     * @return количество сохраненных сущностей
     */
    private int saveProducts(List<ImportableEntity> entities) {
        List<Product> products = filterEntitiesByType(entities, Product.class);
        return productService.saveProducts(products);
    }

    /**
     * Сохраняет данные регионов
     *
     * @param entities список сущностей
     * @return количество сохраненных сущностей
     */
    private int saveRegionData(List<ImportableEntity> entities) {
        List<RegionData> regionDataList = filterEntitiesByType(entities, RegionData.class);
        return regionDataService.saveRegionDataList(regionDataList);
    }

    /**
     * Сохраняет данные конкурентов
     *
     * @param entities список сущностей
     * @return количество сохраненных сущностей
     */
    private int saveCompetitorData(List<ImportableEntity> entities) {
        List<Competitor> competitorList = filterEntitiesByType(entities, Competitor.class);
        return competitorDataService.saveCompetitorDataList(competitorList);
    }

    /**
     * Фильтрует сущности по типу
     *
     * @param entities список сущностей
     * @param type тип сущностей
     * @return отфильтрованный список сущностей указанного типа
     */
    private <T extends ImportableEntity> List<T> filterEntitiesByType(List<ImportableEntity> entities, Class<T> type) {
        return entities.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
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
        validateSaverRegistrationParams(entityType, saverFunction);

        String type = entityType.toLowerCase();
        log.debug("Регистрация обработчика для типа сущности: {}", type);
        savers.put(type, saverFunction);
    }

    /**
     * Валидирует параметры при регистрации обработчика
     *
     * @param entityType тип сущности
     * @param saverFunction функция сохранения
     */
    private void validateSaverRegistrationParams(String entityType, Function<List<ImportableEntity>, Integer> saverFunction) {
        if (entityType == null || saverFunction == null) {
            throw new IllegalArgumentException("Тип сущности и функция сохранения не могут быть null");
        }
    }

    @Override
    public boolean hasSaver(String entityType) {
        if (entityType == null) {
            return false;
        }

        return savers.containsKey(entityType.toLowerCase());
    }
}