package my.java.service.file.builder;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.model.enums.DataSourceType;
import my.java.service.file.metadata.EntityFieldService;
import my.java.service.file.transformer.ValueTransformerFactory;

import java.util.*;

/**
 * Строитель для создания продукта вместе со связанными данными о регионах и конкурентах.
 */
@Slf4j
public class ProductWithRelatedEntitiesBuilder implements EntitySetBuilder {

    private final ValueTransformerFactory transformerFactory;
    private final EntityFieldService entityFieldService;

    private Product product;
    private RegionData regionData;
    private Competitor competitor;

    private Long clientId;
    private Long fileId;

    // Маппинг между заголовками файла и полями сущностей
    private Map<String, String> fileHeaderToEntityField;

    public ProductWithRelatedEntitiesBuilder(ValueTransformerFactory transformerFactory, EntityFieldService entityFieldService) {
        this.transformerFactory = transformerFactory;
        this.entityFieldService = entityFieldService;

        // Инициализируем пустой маппинг заголовков файла
        this.fileHeaderToEntityField = new HashMap<>();

        reset();
    }

    @Override
    public boolean applyRow(Map<String, String> row) {
        boolean hasProductData = applyProductData(row);
        boolean hasRegionData = applyRegionData(row);
        boolean hasCompetitorData = applyCompetitorData(row);

        // Возвращаем true, если хотя бы одна сущность была заполнена
        return hasProductData || hasRegionData || hasCompetitorData;
    }

    private boolean applyProductData(Map<String, String> row) {
        // Собираем данные, относящиеся к продукту
        Map<String, String> productData = extractEntityData(row, "product");

        if (productData.isEmpty()) {
            return false;
        }

        // Инициализируем продукт, если он еще не создан
        initializeProduct();

        // Заполняем поля продукта
        product.fillFromMap(productData);

        return true;
    }

    /**
     * Инициализирует продукт, если он еще не создан
     */
    private void initializeProduct() {
        if (product == null) {
            product = new Product();
            product.setTransformerFactory(transformerFactory);
        }

        if (clientId != null) {
            product.setClientId(clientId);
        }

        if (fileId != null) {
            product.setFileId(fileId);
        }

        product.setDataSource(DataSourceType.FILE);
    }

    private boolean applyRegionData(Map<String, String> row) {
        // Проверяем, есть ли данные о регионе в строке
        if (!rowContainsRegionData(row)) {
            return false;
        }

        // Извлекаем данные региона
        Map<String, String> regionDataMap = extractEntityData(row, "region");

        if (regionDataMap.isEmpty()) {
            return false;
        }

        // Инициализируем данные о регионе, если они еще не созданы
        initializeRegionData();

        // Заполняем поля региона
        regionData.fillFromMap(regionDataMap);

        return true;
    }

    /**
     * Проверяет, содержит ли строка данные о регионе
     *
     * @param row строка данных
     * @return true, если строка содержит данные о регионе
     */
    private boolean rowContainsRegionData(Map<String, String> row) {
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null && (entityField.equals("region") || entityField.equals("regionAddress"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Инициализирует данные о регионе, если они еще не созданы
     */
    private void initializeRegionData() {
        if (regionData == null) {
            regionData = new RegionData();
            regionData.setTransformerFactory(transformerFactory);
        }

        if (clientId != null) {
            regionData.setClientId(clientId);
        }
    }

    private boolean applyCompetitorData(Map<String, String> row) {
        // Проверяем, есть ли данные о конкуренте в строке
        if (!rowContainsCompetitorData(row)) {
            return false;
        }

        // Извлекаем данные конкурента
        Map<String, String> competitorDataMap = extractEntityData(row, "competitor");

        if (competitorDataMap.isEmpty()) {
            return false;
        }

        // Инициализируем данные о конкуренте, если они еще не созданы
        initializeCompetitorData();

        // Заполняем поля конкурента
        competitor.fillFromMap(competitorDataMap);

        return true;
    }

    /**
     * Проверяет, содержит ли строка данные о конкуренте
     *
     * @param row строка данных
     * @return true, если строка содержит данные о конкуренте
     */
    private boolean rowContainsCompetitorData(Map<String, String> row) {
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null && entityField.startsWith("competitor")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Инициализирует данные о конкуренте, если они еще не созданы
     */
    private void initializeCompetitorData() {
        if (competitor == null) {
            competitor = new Competitor();
            competitor.setTransformerFactory(transformerFactory);
        }

        if (clientId != null) {
            competitor.setClientId(clientId);
        }
    }

    /**
     * Извлекает данные для определенной сущности из строки
     *
     * @param row строка данных
     * @param entityPrefix префикс полей сущности
     * @return карта с данными сущности
     */
    private Map<String, String> extractEntityData(Map<String, String> row, String entityPrefix) {
        Map<String, String> entityData = new HashMap<>();

        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null &&
                    (entityField.startsWith(entityPrefix) ||
                            entityPrefix.equals("region") && (entityField.equals("region") || entityField.equals("regionAddress")))) {
                entityData.put(entityField, value);
            }
        }

        return entityData;
    }

    /**
     * Устанавливает маппинги полей из параметров.
     *
     * @param params параметры с маппингами
     * @return this для цепочки вызовов
     */
    public ProductWithRelatedEntitiesBuilder withMappings(Map<String, String> params) {
        if (params == null) {
            return this;
        }

        // Очищаем существующий маппинг заголовков
        fileHeaderToEntityField.clear();

        // Обрабатываем все параметры маппинга
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("mapping[") && key.endsWith("]")) {
                // Извлекаем имя исходного поля из ключа (между "mapping[" и "]")
                String fileHeader = key.substring(8, key.length() - 1);
                String entityField = entry.getValue();

                // Пропускаем пустые значения
                if (entityField == null || entityField.trim().isEmpty()) {
                    continue;
                }

                // Добавляем в маппинг заголовков
                fileHeaderToEntityField.put(fileHeader, entityField);

                // Логируем маппинг для отладки
                log.debug("Добавлен маппинг заголовка: '{}' -> '{}'", fileHeader, entityField);
            }
        }

        log.info("Установлено {} маппингов заголовков файла", fileHeaderToEntityField.size());
        return this;
    }

    @Override
    public EntitySetBuilder withClientId(Long clientId) {
        this.clientId = clientId;
        return this;
    }

    @Override
    public EntitySetBuilder withFileId(Long fileId) {
        this.fileId = fileId;
        return this;
    }

    @Override
    public List<ImportableEntity> build() {
        List<ImportableEntity> entities = new ArrayList<>();

        // Добавляем продукт, если он был создан
        if (product != null) {
            entities.add(product);

            // Устанавливаем связи, если есть регион или конкурент
            if (regionData != null) {
                regionData.setProduct(product);
                entities.add(regionData);
            }

            if (competitor != null) {
                competitor.setProduct(product);
                entities.add(competitor);
            }
        }

        return entities;
    }

    @Override
    public String validate() {
        // Проверяем продукт
        if (product != null) {
            String productValidation = product.validate();
            if (productValidation != null) {
                return productValidation;
            }
        } else {
            return "Отсутствуют данные о продукте";
        }

        // Проверяем регион, если он есть
        if (regionData != null) {
            String regionValidation = regionData.validate();
            if (regionValidation != null) {
                return regionValidation;
            }
        }

        // Проверяем данные о конкуренте, если они есть
        if (competitor != null) {
            String competitorValidation = competitor.validate();
            if (competitorValidation != null) {
                return competitorValidation;
            }
        }

        return null;
    }

    @Override
    public void reset() {
        product = null;
        regionData = null;
        competitor = null;
    }
}