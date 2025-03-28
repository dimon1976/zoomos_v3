package my.java.service.file.builder;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.CompetitorData;
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
    private CompetitorData competitorData;

    private Long clientId;
    private Long fileId;

    // Динамические маппинги, которые могут быть переопределены
    private Map<String, String> productFieldMapping;
    private Map<String, String> regionFieldMapping;
    private Map<String, String> competitorFieldMapping;

    // Маппинг между заголовками файла и полями сущностей
    private Map<String, String> fileHeaderToEntityField;

    public ProductWithRelatedEntitiesBuilder(ValueTransformerFactory transformerFactory, EntityFieldService entityFieldService) {
        this.transformerFactory = transformerFactory;
        this.entityFieldService = entityFieldService;

        // Инициализируем маппинги из сервиса
        this.productFieldMapping = entityFieldService.getFieldMappings("product");
        this.regionFieldMapping = entityFieldService.getFieldMappings("regiondata");
        this.competitorFieldMapping = entityFieldService.getFieldMappings("competitordata");

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
        boolean hasData = false;

        // Создаем продукт, если еще не создан
        if (product == null) {
            product = new Product();
            product.setTransformerFactory(transformerFactory);
        }

        // Применяем клиентский ID и ID файла
        if (clientId != null) {
            product.setClientId(clientId);
        }

        if (fileId != null) {
            product.setFileId(fileId);
        }

        product.setDataSource(DataSourceType.FILE);

        // Заполняем поля продукта на основе маппинга заголовков файла
        Map<String, String> productData = new HashMap<>();

        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            // Получаем имя поля сущности из маппинга заголовков
            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null && entityField.startsWith("product")) {
                hasData = true;
                productData.put(entityField, value);
            }
        }

        if (hasData) {
            product.fillFromMap(productData);
        }

        return hasData;
    }

    private boolean applyRegionData(Map<String, String> row) {
        boolean hasData = false;

        // Проверяем, есть ли данные для региона на основе маппинга заголовков
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null && (entityField.equals("region") || entityField.equals("regionAddress"))) {
                hasData = true;
                break;
            }
        }

        if (!hasData) {
            return false;
        }

        // Создаем регион, если еще не создан
        if (regionData == null) {
            regionData = new RegionData();
            regionData.setTransformerFactory(transformerFactory);
        }

        // Применяем клиентский ID
        if (clientId != null) {
            regionData.setClientId(clientId);
        }

        // Заполняем поля региона
        Map<String, String> regionDataMap = new HashMap<>();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null && (entityField.equals("region") || entityField.equals("regionAddress"))) {
                regionDataMap.put(entityField, value);
            }
        }

        if (!regionDataMap.isEmpty()) {
            regionData.fillFromMap(regionDataMap);
        }

        return !regionDataMap.isEmpty();
    }

    private boolean applyCompetitorData(Map<String, String> row) {
        boolean hasData = false;

        // Проверяем, есть ли данные для конкурента на основе маппинга заголовков
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null && entityField.startsWith("competitor")) {
                hasData = true;
                break;
            }
        }

        if (!hasData) {
            return false;
        }

        // Создаем данные о конкуренте, если еще не созданы
        if (competitorData == null) {
            competitorData = new CompetitorData();
            competitorData.setTransformerFactory(transformerFactory);
        }

        // Применяем клиентский ID
        if (clientId != null) {
            competitorData.setClientId(clientId);
        }

        // Заполняем поля конкурента
        Map<String, String> competitorDataMap = new HashMap<>();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null && entityField.startsWith("competitor")) {
                competitorDataMap.put(entityField, value);
            }
        }

        if (!competitorDataMap.isEmpty()) {
            competitorData.fillFromMap(competitorDataMap);
        }

        return !competitorDataMap.isEmpty();
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

            if (competitorData != null) {
                competitorData.setProduct(product);
                entities.add(competitorData);
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
        if (competitorData != null) {
            String competitorValidation = competitorData.validate();
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
        competitorData = null;
    }
}