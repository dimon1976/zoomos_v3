package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.service.file.transformer.ValueTransformerFactory;

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
public class CompetitorData implements ImportableEntity {

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

    // Статическая карта сопоставления заголовков файла с полями сущности
    private static final Map<String, String> FIELD_MAPPINGS = new HashMap<>();

    static {
        // Инициализация маппинга заголовков и полей
        FIELD_MAPPINGS.put("Сайт", "competitorName");
        FIELD_MAPPINGS.put("Цена конкурента", "competitorPrice");
        FIELD_MAPPINGS.put("Акционная цена", "competitorPromotionalPrice");
        FIELD_MAPPINGS.put("Время", "competitorTime");
        FIELD_MAPPINGS.put("Дата", "competitorDate");
        FIELD_MAPPINGS.put("Дата:Время", "competitorLocalDateTime");
        FIELD_MAPPINGS.put("Статус", "competitorStockStatus");
        FIELD_MAPPINGS.put("Дополнительная цена конкурента", "competitorAdditionalPrice");
        FIELD_MAPPINGS.put("Комментарий", "competitorCommentary");
        FIELD_MAPPINGS.put("Наименование товара конкурента", "competitorProductName");
        FIELD_MAPPINGS.put("Дополнительное поле", "competitorAdditional");
        FIELD_MAPPINGS.put("Дополнительное поле 2", "competitorAdditional2");
        FIELD_MAPPINGS.put("Ссылка", "competitorUrl");
        FIELD_MAPPINGS.put("Скриншот", "competitorWebCacheUrl");
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
            String key = entry.getKey();
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            // Проверяем, является ли ключ именем поля напрямую
            boolean isFieldName = false;
            for (String fieldName : FIELD_MAPPINGS.values()) {
                if (fieldName.equals(key)) {
                    success &= setFieldValue(key, value);
                    isFieldName = true;
                    break;
                }
            }

            // Если ключ не является именем поля, пробуем найти соответствие в маппинге
            if (!isFieldName) {
                String fieldName = FIELD_MAPPINGS.get(key);
                if (fieldName == null) {
                    // Пробуем без учета регистра
                    for (Map.Entry<String, String> mapping : FIELD_MAPPINGS.entrySet()) {
                        if (mapping.getKey().equalsIgnoreCase(key)) {
                            fieldName = mapping.getValue();
                            break;
                        }
                    }
                }

                if (fieldName != null) {
                    success &= setFieldValue(fieldName, value);
                }
            }
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
        return new HashMap<>(FIELD_MAPPINGS);
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