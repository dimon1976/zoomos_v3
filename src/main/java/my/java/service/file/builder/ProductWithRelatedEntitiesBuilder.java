package my.java.service.file.builder;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.MarketData;
import my.java.model.entity.Product;
import my.java.model.enums.DataSourceType;
import my.java.service.file.metadata.EntityFieldService;
import my.java.service.file.transformer.ValueTransformerFactory;

import java.util.*;

/**
 * Строитель для создания продукта вместе со связанными данными о регионах и конкурентах.
 * Обновлен для работы с объединенной сущностью MarketData.
 */
@Slf4j
public class ProductWithRelatedEntitiesBuilder implements EntitySetBuilder {

    private final ValueTransformerFactory transformerFactory;
    private final EntityFieldService entityFieldService;

    private Product product;
    private List<MarketData> marketDataList;

    private Long clientId;
    private Long fileId;

    // Динамические маппинги, которые могут быть переопределены
    private Map<String, String> productFieldMapping;
    private Map<String, String> marketDataFieldMapping;

    // Маппинг между заголовками файла и полями сущностей
    private Map<String, String> fileHeaderToEntityField;

    public ProductWithRelatedEntitiesBuilder(ValueTransformerFactory transformerFactory, EntityFieldService entityFieldService) {
        this.transformerFactory = transformerFactory;
        this.entityFieldService = entityFieldService;

        // Инициализируем маппинги из сервиса
        this.productFieldMapping = entityFieldService.getFieldMappings("product");

        // Объединяем маппинги из regiondata и competitordata
        this.marketDataFieldMapping = new HashMap<>();
        this.marketDataFieldMapping.putAll(entityFieldService.getFieldMappings("regiondata"));
        this.marketDataFieldMapping.putAll(entityFieldService.getFieldMappings("competitordata"));

        // Инициализируем пустой маппинг заголовков файла
        this.fileHeaderToEntityField = new HashMap<>();

        reset();
    }

    @Override
    public boolean applyRow(Map<String, String> row) {
        boolean hasProductData = applyProductData(row);
        boolean hasMarketData = applyMarketData(row);

        // Возвращаем true, если хотя бы одна сущность была заполнена
        return hasProductData || hasMarketData;
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

    private boolean applyMarketData(Map<String, String> row) {
        // Проверяем наличие данных для региона или конкурента
        boolean hasRegionData = hasRegionData(row);
        boolean hasCompetitorData = hasCompetitorData(row);

        if (!hasRegionData && !hasCompetitorData) {
            return false;
        }

        // Создаем экземпляр MarketData
        MarketData marketData = new MarketData();
        marketData.setTransformerFactory(transformerFactory);

        // Применяем клиентский ID
        if (clientId != null) {
            marketData.setClientId(clientId);
        }

        // Заполняем поля маркетинговых данных
        Map<String, String> marketDataMap = new HashMap<>();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fileHeader = entry.getKey();
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String entityField = fileHeaderToEntityField.get(fileHeader);
            if (entityField != null &&
                    (entityField.equals("region") ||
                            entityField.equals("regionAddress") ||
                            entityField.startsWith("competitor"))) {
                marketDataMap.put(entityField, value);
            }
        }

        if (!marketDataMap.isEmpty()) {
            marketData.fillFromMap(marketDataMap);
            marketDataList.add(marketData);
            return true;
        }

        return false;
    }

    private boolean hasRegionData(Map<String, String> row) {
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

    private boolean hasCompetitorData(Map<String, String> row) {
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

            // Устанавливаем связи с маркетинговыми данными
            for (MarketData marketData : marketDataList) {
                marketData.setProduct(product);
                entities.add(marketData);
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

        // Проверяем маркетинговые данные
        for (MarketData marketData : marketDataList) {
            String marketDataValidation = marketData.validate();
            if (marketDataValidation != null) {
                return marketDataValidation;
            }
        }

        return null;
    }

    @Override
    public void reset() {
        product = null;
        marketDataList = new ArrayList<>();
    }
}