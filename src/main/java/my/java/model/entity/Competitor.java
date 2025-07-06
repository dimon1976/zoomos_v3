package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.util.transformer.ValueTransformerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Сущность, представляющая данные о конкуренте.
 */
@Setter
@Getter
@Entity
@Table(name = "competitor_data")
public class Competitor implements ImportableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clientId;

    @Column(length = 400)
    private String competitorName;

    private String competitorPrice;
    private String competitorPromotionalPrice;
    private String competitorTime;
    private String competitorDate;
    private LocalDateTime competitorLocalDateTime;
    private String competitorStockStatus;
    private String competitorAdditionalPrice;

    @Column(length = 1000)
    private String competitorCommentary;

    @Column(length = 400)
    private String competitorProductName;

    private String competitorAdditional;
    private String competitorAdditional2;

    @Column(length = 1200)
    private String competitorUrl;

    @Column(length = 1200)
    private String competitorWebCacheUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

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
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Сайт", "competitorName");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Цена конкурента", "competitorPrice");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Акционная цена", "competitorPromotionalPrice");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Время", "competitorTime");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дата", "competitorDate");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дата:Время", "competitorLocalDateTime");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Статус", "competitorStockStatus");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительная цена конкурента", "competitorAdditionalPrice");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Комментарий", "competitorCommentary");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Наименование товара конкурента", "competitorProductName");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле", "competitorAdditional");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Дополнительное поле 2", "competitorAdditional2");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Ссылка", "competitorUrl");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Скриншот", "competitorWebCacheUrl");
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
            String fieldName = UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.get(header);
            if (fieldName == null) {
                // Пробуем без учета регистра
                for (Map.Entry<String, String> mapping : UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.entrySet()) {
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
                case "competitorName":
                    this.competitorName = value;
                    break;
                case "competitorPrice":
                    this.competitorPrice = value;
                    break;
                case "competitorPromotionalPrice":
                    this.competitorPromotionalPrice = value;
                    break;
                case "competitorTime":
                    this.competitorTime = value;
                    break;
                case "competitorDate":
                    this.competitorDate = value;
                    break;
                case "competitorLocalDateTime":
                    this.competitorLocalDateTime = transformerFactory.transform(value, LocalDateTime.class, null);
                    break;
                case "competitorStockStatus":
                    this.competitorStockStatus = value;
                    break;
                case "competitorAdditionalPrice":
                    this.competitorAdditionalPrice = value;
                    break;
                case "competitorCommentary":
                    this.competitorCommentary = value;
                    break;
                case "competitorProductName":
                    this.competitorProductName = value;
                    break;
                case "competitorAdditional":
                    this.competitorAdditional = value;
                    break;
                case "competitorAdditional2":
                    this.competitorAdditional2 = value;
                    break;
                case "competitorUrl":
                    this.competitorUrl = value;
                    break;
                case "competitorWebCacheUrl":
                    this.competitorWebCacheUrl = value;
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
        if (competitorName == null || competitorName.trim().isEmpty()) {
            return "Не указано название сайта конкурента";
        }
        return null;
    }
}