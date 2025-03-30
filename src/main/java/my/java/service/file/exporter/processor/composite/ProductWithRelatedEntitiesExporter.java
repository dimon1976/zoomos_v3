package my.java.service.file.exporter.processor.composite;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.repository.CompetitorDataRepository;
import my.java.repository.RegionDataRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Класс для объединения продукта и связанных сущностей (регионы, конкуренты) в одну сущность для экспорта.
 * Путь: /java/my/java/service/file/exporter/processor/composite/ProductWithRelatedEntitiesExporter.java
 */
@Slf4j
public class ProductWithRelatedEntitiesExporter {

    private final RegionDataRepository regionDataRepository;
    private final CompetitorDataRepository competitorDataRepository;

    /**
     * Конструктор для создания экспортера связанных сущностей
     *
     * @param regionDataRepository репозиторий для доступа к данным о регионах
     * @param competitorDataRepository репозиторий для доступа к данным о конкурентах
     */
    public ProductWithRelatedEntitiesExporter(
            RegionDataRepository regionDataRepository,
            CompetitorDataRepository competitorDataRepository) {
        this.regionDataRepository = regionDataRepository;
        this.competitorDataRepository = competitorDataRepository;
    }

    /**
     * Преобразует список отдельных продуктов в список составных сущностей, включающих
     * продукт со связанными регионами и конкурентами.
     *
     * @param products список продуктов
     * @return список составных сущностей для экспорта
     */
    public List<CompositeProductEntity> convertToCompositeEntities(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("Преобразование {} продуктов в составные сущности для экспорта", products.size());

        // Собираем все идентификаторы продуктов
        List<Long> productIds = products.stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        // Загружаем все связанные данные о регионах по идентификаторам продуктов
        Map<Long, List<RegionData>> productToRegionsMap = loadRegionsForProducts(productIds);

        // Загружаем все связанные данные о конкурентах по идентификаторам продуктов
        Map<Long, List<Competitor>> productToCompetitorsMap = loadCompetitorsForProducts(productIds);

        // Создаем составные сущности для каждого продукта
        List<CompositeProductEntity> result = new ArrayList<>();

        for (Product product : products) {
            Long productId = product.getId();

            // Получаем связанные регионы и конкуренты
            List<RegionData> regions = productToRegionsMap.getOrDefault(productId, Collections.emptyList());
            List<Competitor> competitors = productToCompetitorsMap.getOrDefault(productId, Collections.emptyList());

            // Если у продукта нет связанных данных, создаем одну составную сущность только с продуктом
            if (regions.isEmpty() && competitors.isEmpty()) {
                result.add(new CompositeProductEntity(product, null, null));
            } else if (regions.isEmpty()) {
                // Если есть только конкуренты
                for (Competitor competitor : competitors) {
                    result.add(new CompositeProductEntity(product, null, competitor));
                }
            } else if (competitors.isEmpty()) {
                // Если есть только регионы
                for (RegionData region : regions) {
                    result.add(new CompositeProductEntity(product, region, null));
                }
            } else {
                // Если есть и регионы, и конкуренты, создаем комбинации
                for (RegionData region : regions) {
                    for (Competitor competitor : competitors) {
                        result.add(new CompositeProductEntity(product, region, competitor));
                    }
                }
            }
        }

        log.info("Создано {} составных сущностей для экспорта", result.size());
        return result;
    }

    /**
     * Загружает данные о регионах для списка продуктов
     *
     * @param productIds идентификаторы продуктов
     * @return карта "идентификатор продукта -> список регионов"
     */
    private Map<Long, List<RegionData>> loadRegionsForProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Загружаем все связанные регионы
        List<RegionData> allRegions = regionDataRepository.findByProductIdIn(productIds);
        log.debug("Загружено {} регионов для {} продуктов", allRegions.size(), productIds.size());

        // Группируем регионы по ID продукта
        Map<Long, List<RegionData>> result = new HashMap<>();
        for (RegionData region : allRegions) {
            Long productId = region.getProduct().getId();
            if (!result.containsKey(productId)) {
                result.put(productId, new ArrayList<>());
            }
            result.get(productId).add(region);
        }

        return result;
    }

    /**
     * Загружает данные о конкурентах для списка продуктов
     *
     * @param productIds идентификаторы продуктов
     * @return карта "идентификатор продукта -> список конкурентов"
     */
    private Map<Long, List<Competitor>> loadCompetitorsForProducts(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Загружаем всех связанных конкурентов
        List<Competitor> allCompetitors = competitorDataRepository.findByProductIdIn(productIds);
        log.debug("Загружено {} конкурентов для {} продуктов", allCompetitors.size(), productIds.size());

        // Группируем конкурентов по ID продукта
        Map<Long, List<Competitor>> result = new HashMap<>();
        for (Competitor competitor : allCompetitors) {
            Long productId = competitor.getProduct().getId();
            if (!result.containsKey(productId)) {
                result.put(productId, new ArrayList<>());
            }
            result.get(productId).add(competitor);
        }

        return result;
    }

    /**
     * Класс, представляющий составную сущность, которая содержит продукт и связанные с ним
     * данные о регионе и конкуренте.
     */
    public static class CompositeProductEntity implements ImportableEntity {
        private final Product product;
        private final RegionData region;
        private final Competitor competitor;

        public CompositeProductEntity(Product product, RegionData region, Competitor competitor) {
            this.product = product;
            this.region = region;
            this.competitor = competitor;
        }

        public Product getProduct() {
            return product;
        }

        public RegionData getRegion() {
            return region;
        }

        public Competitor getCompetitor() {
            return competitor;
        }

        @Override
        public Map<String, String> getFieldMappings() {
            // Создаем объединенную карту соответствий полей, начиная с продукта
            Map<String, String> mappings = new HashMap<>(product.getFieldMappings());

            // Добавляем поля региона, если он есть, с префиксом "region."
            if (region != null) {
                for (Map.Entry<String, String> entry : region.getFieldMappings().entrySet()) {
                    mappings.put("Region." + entry.getKey(), "region." + entry.getValue());
                }
            }

            // Добавляем поля конкурента, если он есть, с префиксом "competitor."
            if (competitor != null) {
                for (Map.Entry<String, String> entry : competitor.getFieldMappings().entrySet()) {
                    mappings.put("Competitor." + entry.getKey(), "competitor." + entry.getValue());
                }
            }

            return mappings;
        }

        @Override
        public String validate() {
            // Валидация продукта
            if (product == null) {
                return "Отсутствуют данные о продукте";
            }

            String productValidation = product.validate();
            if (productValidation != null) {
                return productValidation;
            }

            // Валидация региона, если он есть
            if (region != null) {
                String regionValidation = region.validate();
                if (regionValidation != null) {
                    return "Ошибка в данных региона: " + regionValidation;
                }
            }

            // Валидация конкурента, если он есть
            if (competitor != null) {
                String competitorValidation = competitor.validate();
                if (competitorValidation != null) {
                    return "Ошибка в данных конкурента: " + competitorValidation;
                }
            }

            return null;
        }

        @Override
        public void fillFromMap(Map<String, String> data) {
            // Этот метод не используется при экспорте
            throw new UnsupportedOperationException("Метод не поддерживается для экспорта");
        }
    }
}