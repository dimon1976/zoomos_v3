package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.model.enums.DataSourceType;

import java.util.*;

/**
 * Модифицированная сущность, представляющая товар в системе.
 * Наследуется от AbstractImportableEntity для переиспользования общего кода.
 */
@Setter
@Getter
@Entity
@Table(name = "products")
public class Product extends AbstractImportableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source")
    private DataSourceType dataSource;

    private Long fileId;
    private Long clientId;

    // Основные поля товара
    private String productId;

    @Column(length = 400)
    private String productName;

    private String productBrand;
    private String productBar;
    private String productDescription;

    @Column(length = 1100)
    private String productUrl;

    private String productCategory1;
    private String productCategory2;
    private String productCategory3;
    private Double productPrice;
    private String productAnalog;

    // Дополнительные поля
    private String productAdditional1;
    private String productAdditional2;
    private String productAdditional3;
    private String productAdditional4;
    private String productAdditional5;

    // Единый список для рыночных данных (заменяет отдельные списки RegionData и CompetitorData)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MarketData> marketDataList = new ArrayList<>();

    // Статическая карта сопоставления заголовков файла с полями сущности
    private static final Map<String, String> FIELD_MAPPINGS = new HashMap<>();

    static {
        // Инициализация маппинга заголовков и полей
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
    }

    /**
     * Устанавливает значение поля по его имени.
     *
     * @param fieldName имя поля
     * @param value строковое значение
     * @return true, если значение успешно установлено
     */
    @Override
    protected boolean setFieldValue(String fieldName, String value) {
        try {
            switch (fieldName) {
                case "productId":
                    this.productId = value;
                    break;
                case "productName":
                    this.productName = value;
                    break;
                case "productBrand":
                    this.productBrand = value;
                    break;
                case "productBar":
                    this.productBar = value;
                    break;
                case "productDescription":
                    this.productDescription = value;
                    break;
                case "productUrl":
                    this.productUrl = value;
                    break;
                case "productCategory1":
                    this.productCategory1 = value;
                    break;
                case "productCategory2":
                    this.productCategory2 = value;
                    break;
                case "productCategory3":
                    this.productCategory3 = value;
                    break;
                case "productPrice":
                    this.productPrice = transformerFactory.transform(value, Double.class, null);
                    break;
                case "productAnalog":
                    this.productAnalog = value;
                    break;
                case "productAdditional1":
                    this.productAdditional1 = value;
                    break;
                case "productAdditional2":
                    this.productAdditional2 = value;
                    break;
                case "productAdditional3":
                    this.productAdditional3 = value;
                    break;
                case "productAdditional4":
                    this.productAdditional4 = value;
                    break;
                case "productAdditional5":
                    this.productAdditional5 = value;
                    break;
                default:
                    return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Возвращает карту соответствия заголовков файла и полей сущности.
     *
     * @return карта, где ключ - заголовок файла, значение - имя поля в сущности
     */
    @Override
    public Map<String, String> getFieldMappings() {
        return new HashMap<>(FIELD_MAPPINGS);
    }

    /**
     * Валидирует заполненную сущность.
     *
     * @return null, если валидация прошла успешно, иначе - сообщение об ошибке
     */
    @Override
    public String validate() {
        if (productName == null || productName.trim().isEmpty()) {
            return "Не указано название товара";
        }
        return null;
    }

    /**
     * Вспомогательный метод для работы с рыночными данными
     */
    public void addMarketData(MarketData marketData) {
        marketDataList.add(marketData);
        marketData.setProduct(this);
    }

    /**
     * Вспомогательный метод для работы с рыночными данными
     */
    public void removeMarketData(MarketData marketData) {
        marketDataList.remove(marketData);
        marketData.setProduct(null);
    }
}