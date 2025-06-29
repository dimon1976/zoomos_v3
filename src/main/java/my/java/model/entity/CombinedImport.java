package my.java.model.entity;

import lombok.Getter;
import lombok.Setter;
import my.java.util.transformer.ValueTransformerFactory;

import jakarta.persistence.Transient;
import java.util.HashMap;
import java.util.Map;

/**
 * Виртуальная сущность для комбинированного импорта данных
 * (Product + Region + Competitor в одном файле)
 */
@Getter
@Setter
public class CombinedImport implements ImportableEntity {

    // Поля Product
    private String productId;
    private String productName;
    private String productBrand;
    private String productBar;
    private String productDescription;
    private String productUrl;
    private String productCategory1;
    private String productCategory2;
    private String productCategory3;
    private Double productPrice;
    private String productAnalog;
    private String productAdditional1;
    private String productAdditional2;
    private String productAdditional3;
    private String productAdditional4;
    private String productAdditional5;

    // Поля Region
    private String region;
    private String regionAddress;

    // Поля Competitor
    private String competitorName;
    private String competitorPrice;
    private String competitorPromotionalPrice;
    private String competitorTime;
    private String competitorDate;
    private String competitorStockStatus;
    private String competitorAdditionalPrice;
    private String competitorCommentary;
    private String competitorProductName;
    private String competitorAdditional;
    private String competitorAdditional2;
    private String competitorUrl;
    private String competitorWebCacheUrl;

    @Transient
    private ValueTransformerFactory transformerFactory;

    private Long clientId;

    // Объединенная карта всех маппингов
    private static final Map<String, String> FIELD_MAPPINGS = new HashMap<>();

    static {
        // Маппинги для Product
        FIELD_MAPPINGS.put("ID товара", "productId");
        FIELD_MAPPINGS.put("Модель", "productName");
        FIELD_MAPPINGS.put("Бренд", "productBrand");
        FIELD_MAPPINGS.put("Штрихкод", "productBar");
        FIELD_MAPPINGS.put("Описание", "productDescription");
        FIELD_MAPPINGS.put("Ссылка", "productUrl");
        FIELD_MAPPINGS.put("Категория товара 1", "productCategory1");
        FIELD_MAPPINGS.put("Категория товара 2", "productCategory2");
        FIELD_MAPPINGS.put("Категория товара 3", "productCategory3");
        FIELD_MAPPINGS.put("Цена", "productPrice");
        FIELD_MAPPINGS.put("Аналог", "productAnalog");
        FIELD_MAPPINGS.put("Дополнительное поле 1", "productAdditional1");
        FIELD_MAPPINGS.put("Дополнительное поле 2", "productAdditional2");
        FIELD_MAPPINGS.put("Дополнительное поле 3", "productAdditional3");
        FIELD_MAPPINGS.put("Дополнительное поле 4", "productAdditional4");
        FIELD_MAPPINGS.put("Дополнительное поле 5", "productAdditional5");

        // Маппинги для Region
        FIELD_MAPPINGS.put("Город", "region");
        FIELD_MAPPINGS.put("Адрес", "regionAddress");

        // Маппинги для Competitor
        FIELD_MAPPINGS.put("Сайт", "competitorName");
        FIELD_MAPPINGS.put("Цена конкурента", "competitorPrice");
        FIELD_MAPPINGS.put("Акционная цена", "competitorPromotionalPrice");
        FIELD_MAPPINGS.put("Время", "competitorTime");
        FIELD_MAPPINGS.put("Дата", "competitorDate");
        FIELD_MAPPINGS.put("Статус", "competitorStockStatus");
        FIELD_MAPPINGS.put("Дополнительная цена конкурента", "competitorAdditionalPrice");
        FIELD_MAPPINGS.put("Комментарий", "competitorCommentary");
        FIELD_MAPPINGS.put("Наименование товара конкурента", "competitorProductName");
        FIELD_MAPPINGS.put("Дополнительное поле", "competitorAdditional");
        FIELD_MAPPINGS.put("Дополнительное поле 2", "competitorAdditional2");
        FIELD_MAPPINGS.put("Ссылка на конкурента", "competitorUrl");
        FIELD_MAPPINGS.put("Скриншот", "competitorWebCacheUrl");
    }

    @Override
    public boolean fillFromMap(Map<String, String> data) {
        if (transformerFactory == null) {
            throw new IllegalStateException("TransformerFactory не установлен");
        }

        boolean success = true;

        for (Map.Entry<String, String> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            success &= setFieldValue(fieldName, value);
        }

        return success;
    }

    private boolean setFieldValue(String fieldName, String value) {
        try {
            switch (fieldName) {
                // Product fields
                case "productId": this.productId = value; break;
                case "productName": this.productName = value; break;
                case "productBrand": this.productBrand = value; break;
                case "productBar": this.productBar = value; break;
                case "productDescription": this.productDescription = value; break;
                case "productUrl": this.productUrl = value; break;
                case "productCategory1": this.productCategory1 = value; break;
                case "productCategory2": this.productCategory2 = value; break;
                case "productCategory3": this.productCategory3 = value; break;
                case "productPrice":
                    this.productPrice = transformerFactory.transform(value, Double.class, null);
                    break;
                case "productAnalog": this.productAnalog = value; break;
                case "productAdditional1": this.productAdditional1 = value; break;
                case "productAdditional2": this.productAdditional2 = value; break;
                case "productAdditional3": this.productAdditional3 = value; break;
                case "productAdditional4": this.productAdditional4 = value; break;
                case "productAdditional5": this.productAdditional5 = value; break;

                // Region fields
                case "region": this.region = value; break;
                case "regionAddress": this.regionAddress = value; break;

                // Competitor fields
                case "competitorName": this.competitorName = value; break;
                case "competitorPrice": this.competitorPrice = value; break;
                case "competitorPromotionalPrice": this.competitorPromotionalPrice = value; break;
                case "competitorTime": this.competitorTime = value; break;
                case "competitorDate": this.competitorDate = value; break;
                case "competitorStockStatus": this.competitorStockStatus = value; break;
                case "competitorAdditionalPrice": this.competitorAdditionalPrice = value; break;
                case "competitorCommentary": this.competitorCommentary = value; break;
                case "competitorProductName": this.competitorProductName = value; break;
                case "competitorAdditional": this.competitorAdditional = value; break;
                case "competitorAdditional2": this.competitorAdditional2 = value; break;
                case "competitorUrl": this.competitorUrl = value; break;
                case "competitorWebCacheUrl": this.competitorWebCacheUrl = value; break;

                default: return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Map<String, String> getFieldMappings() {
        return new HashMap<>(FIELD_MAPPINGS);
    }

    @Override
    public String validate() {
        // При комбинированном импорте проверяем наличие хотя бы одного основного поля
        boolean hasProductData = productName != null && !productName.trim().isEmpty();
        boolean hasRegionData = region != null && !region.trim().isEmpty();
        boolean hasCompetitorData = competitorName != null && !competitorName.trim().isEmpty();

        if (!hasProductData && !hasRegionData && !hasCompetitorData) {
            return "Не заполнено ни одно обязательное поле (название товара, город или сайт конкурента)";
        }

        return null;
    }

    /**
     * Проверяет, содержит ли запись данные о товаре
     */
    public boolean hasProductData() {
        return productName != null && !productName.trim().isEmpty();
    }

    /**
     * Проверяет, содержит ли запись данные о регионе
     */
    public boolean hasRegionData() {
        return region != null && !region.trim().isEmpty();
    }

    /**
     * Проверяет, содержит ли запись данные о конкуренте
     */
    public boolean hasCompetitorData() {
        return competitorName != null && !competitorName.trim().isEmpty();
    }

    /**
     * Создает сущность Product из данных
     */
    public Product createProduct() {
        Product product = new Product();
        product.setClientId(clientId);
        product.setProductId(productId);
        product.setProductName(productName);
        product.setProductBrand(productBrand);
        product.setProductBar(productBar);
        product.setProductDescription(productDescription);
        product.setProductUrl(productUrl);
        product.setProductCategory1(productCategory1);
        product.setProductCategory2(productCategory2);
        product.setProductCategory3(productCategory3);
        product.setProductPrice(productPrice);
        product.setProductAnalog(productAnalog);
        product.setProductAdditional1(productAdditional1);
        product.setProductAdditional2(productAdditional2);
        product.setProductAdditional3(productAdditional3);
        product.setProductAdditional4(productAdditional4);
        product.setProductAdditional5(productAdditional5);
        return product;
    }

    /**
     * Создает сущность Region из данных
     */
    public Region createRegion() {
        Region regionEntity = new Region();
        regionEntity.setClientId(clientId);
        regionEntity.setRegion(region);
        regionEntity.setRegionAddress(regionAddress);
        return regionEntity;
    }

    /**
     * Создает сущность Competitor из данных
     */
    public Competitor createCompetitor() {
        Competitor competitor = new Competitor();
        competitor.setClientId(clientId);
        competitor.setCompetitorName(competitorName);
        competitor.setCompetitorPrice(competitorPrice);
        competitor.setCompetitorPromotionalPrice(competitorPromotionalPrice);
        competitor.setCompetitorTime(competitorTime);
        competitor.setCompetitorDate(competitorDate);
        competitor.setCompetitorStockStatus(competitorStockStatus);
        competitor.setCompetitorAdditionalPrice(competitorAdditionalPrice);
        competitor.setCompetitorCommentary(competitorCommentary);
        competitor.setCompetitorProductName(competitorProductName);
        competitor.setCompetitorAdditional(competitorAdditional);
        competitor.setCompetitorAdditional2(competitorAdditional2);
        competitor.setCompetitorUrl(competitorUrl);
        competitor.setCompetitorWebCacheUrl(competitorWebCacheUrl);
        return competitor;
    }
}