package my.java.service.file.strategy.import_strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.CompetitorData;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.service.file.transformer.ValueTransformerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Фабрика для создания сущностей, которые могут быть импортированы из файлов.
 * Реализует паттерн Factory Method для создания сущностей по типу.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ImportableEntityFactory {

    private final ValueTransformerFactory transformerFactory;

    // Карта поставщиков сущностей по типам
    private final Map<String, Supplier<ImportableEntity>> entitySuppliers = new HashMap<>();

    /**
     * Инициализация поставщиков сущностей.
     */
    {
        entitySuppliers.put("product", this::createProduct);
        entitySuppliers.put("competitor", this::createCompetitorData);
        entitySuppliers.put("region", this::createRegionData);
    }

    /**
     * Создает новую сущность указанного типа.
     *
     * @param entityType тип сущности
     * @return созданная сущность или null, если тип не поддерживается
     */
    public ImportableEntity createEntity(String entityType) {
        if (entityType == null || entityType.isEmpty()) {
            log.warn("Тип сущности не указан");
            return null;
        }

        Supplier<ImportableEntity> supplier = entitySuppliers.get(entityType.toLowerCase());
        if (supplier == null) {
            log.warn("Неподдерживаемый тип сущности: {}", entityType);
            return null;
        }

        return supplier.get();
    }

    /**
     * Проверяет, поддерживается ли указанный тип сущности.
     *
     * @param entityType тип сущности
     * @return true, если тип поддерживается
     */
    public boolean isEntityTypeSupported(String entityType) {
        return entityType != null && entitySuppliers.containsKey(entityType.toLowerCase());
    }

    /**
     * Создает новую сущность типа Product.
     *
     * @return новая сущность Product
     */
    private Product createProduct() {
        Product product = new Product();
        product.setTransformerFactory(transformerFactory);
        return product;
    }

    /**
     * Создает новую сущность типа CompetitorData.
     *
     * @return новая сущность CompetitorData
     */
    private CompetitorData createCompetitorData() {
        CompetitorData competitorData = new CompetitorData();
        competitorData.setTransformerFactory(transformerFactory);
        return competitorData;
    }

    /**
     * Создает новую сущность типа RegionData.
     *
     * @return новая сущность RegionData
     */
    private RegionData createRegionData() {
        RegionData regionData = new RegionData();
        regionData.setTransformerFactory(transformerFactory);
        return regionData;
    }

    /**
     * Возвращает класс сущности по типу.
     *
     * @param entityType тип сущности
     * @return класс сущности или null, если тип не поддерживается
     */
    public Class<? extends ImportableEntity> getEntityClass(String entityType) {
        if (entityType == null || entityType.isEmpty()) {
            return null;
        }

        switch (entityType.toLowerCase()) {
            case "product":
                return Product.class;
            case "competitor":
                return CompetitorData.class;
            case "region":
                return RegionData.class;
            default:
                return null;
        }
    }
}