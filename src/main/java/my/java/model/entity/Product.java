package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.model.enums.DataSourceType;
import my.java.util.transformer.ValueTransformerFactory;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.ZonedDateTime;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source")
    private DataSourceType dataSource;

    @Column(name = "operation_id")
    private Long operationId;

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

    // =====================================================================================
    // ВНИМАНИЕ! ЭТО НЕ ЗАГОЛОВКИ CSV ФАЙЛОВ!
    //
    // Это человекочитаемые названия для отображения в интерфейсе при создании маппингов.
    // Пользователь видит эти названия и сопоставляет их с реальными заголовками CSV.
    //
    // Реальные заголовки CSV приходят через FieldMappingDetail.sourceField!
    // =====================================================================================
    private static final Map<String, String> UI_DISPLAY_NAMES_TO_ENTITY_FIELDS = new HashMap<>();

    static {
        // "Как показать пользователю в UI" -> "имя поля в Java сущности"
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("ID товара", "productId");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Модель", "productName");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Бренд", "productBrand");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Штрихкод", "productBar");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Описание", "productDescription");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Ссылка", "productUrl");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория товара 1", "productCategory1");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория товара 2", "productCategory2");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Категория товара 3", "productCategory3");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Цена", "productPrice");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Аналог", "productAnalog");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 1", "productAdditional1");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 2", "productAdditional2");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 3", "productAdditional3");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 4", "productAdditional4");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 5", "productAdditional5");
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
            String fieldName = entry.getKey();  // это уже имя поля Java-сущности
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            // Проверяем, что это валидное имя поля
            if (!isValidFieldName(fieldName)) {
                continue;
            }

            // Устанавливаем значение поля
            success &= setFieldValue(fieldName, value);
        }

        return success;
    }

    /**
     * Проверяет, является ли имя поля валидным для данной сущности
     */
    private boolean isValidFieldName(String fieldName) {
        // Определяем набор валидных имен полей для данной сущности
        Set<String> validFieldNames = Set.of(
                "productId", "productName", "productBrand", "productBar", "productDescription",
                "productUrl", "productCategory1", "productCategory2", "productCategory3",
                "productPrice", "productAnalog", "productAdditional1", "productAdditional2",
                "productAdditional3", "productAdditional4", "productAdditional5"
        );

        return validFieldNames.contains(fieldName);
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
        return new HashMap<>(UI_DISPLAY_NAMES_TO_ENTITY_FIELDS);
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
}