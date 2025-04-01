package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.model.enums.DataSourceType;
import my.java.service.file.transformer.ValueTransformerFactory;

import java.util.*;

/**
 * Сущность, представляющая товар в системе.
 */
@Setter
@Getter
@Entity
@Table(name = "products")
public class Product implements ImportableEntity {

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

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Region> regionList = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Competitor> competitorList = new ArrayList<>();

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

    // Транзитивные поля, не сохраняемые в БД
    @Transient
    private ValueTransformerFactory transformerFactory;

    /**
     * Устанавливает фабрику трансформеров для преобразования строковых значений.
     *
     * @param transformerFactory фабрика трансформеров
     */
    public void setTransformerFactory(ValueTransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    /**
     * Заполняет поля сущности из карты с данными.
     *
     * @param data карта, где ключ - имя поля (или заголовок файла), значение - строковое значение
     * @return true, если заполнение прошло успешно
     */
    @Override
    public boolean fillFromMap(Map<String, String> data) {
        if (transformerFactory == null) {
            throw new IllegalStateException("TransformerFactory не установлен");
        }

        boolean success = true;

        for (Map.Entry<String, String> entry : data.entrySet()) {
            String header = entry.getKey();
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            // Получаем имя поля из маппинга
            String fieldName = FIELD_MAPPINGS.get(header);
            if (fieldName == null) {
                // Пробуем без учета регистра
                for (Map.Entry<String, String> mapping : FIELD_MAPPINGS.entrySet()) {
                    if (mapping.getKey().equalsIgnoreCase(header)) {
                        fieldName = mapping.getValue();
                        break;
                    }
                }

                // Если все еще не нашли, пропускаем
                if (fieldName == null) {
                    continue;
                }
            }

            // Устанавливаем значение поля
            success &= setFieldValue(fieldName, value);
        }

        return success;
    }

    /**
     * Устанавливает значение поля по его имени.
     *
     * @param fieldName имя поля
     * @param value строковое значение
     * @return true, если значение успешно установлено
     */
    private boolean setFieldValue(String fieldName, String value) {
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
     * Вспомогательные методы для установки отношений
     */
    public void addRegionData(Region region) {
        regionList.add(region);
        region.setProduct(this);
    }

    public void removeRegionData(Region region) {
        regionList.remove(region);
        region.setProduct(null);
    }

    public void addCompetitorData(Competitor competitor) {
        competitorList.add(competitor);
        competitor.setProduct(this);
    }

    public void removeCompetitorData(Competitor competitor) {
        competitorList.remove(competitor);
        competitor.setProduct(null);
    }
}