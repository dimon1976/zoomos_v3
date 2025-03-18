package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Объединенная сущность, представляющая региональные данные и данные о конкурентах.
 * Заменяет отдельные классы RegionData и CompetitorData.
 */
@Setter
@Getter
@Entity
@Table(name = "market_data", indexes = {
        @Index(name = "idx_market_region", columnList = "region"),
        @Index(name = "idx_market_competitor", columnList = "competitor_name")
})
public class MarketData extends AbstractImportableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clientId;

    // Связь с продуктом
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // Данные региона
    private String region;

    @Column(length = 400)
    private String regionAddress;

    // Данные конкурента
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

    // Объединенная статическая карта сопоставления заголовков файла с полями сущности
    private static final Map<String, String> FIELD_MAPPINGS = new HashMap<>();

    static {
        // Инициализация из RegionData
        FIELD_MAPPINGS.put("Город", "region");
        FIELD_MAPPINGS.put("Адрес", "regionAddress");

        // Инициализация из CompetitorData
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

    @Override
    public Map<String, String> getFieldMappings() {
        return new HashMap<>(FIELD_MAPPINGS);
    }

    @Override
    protected boolean setFieldValue(String fieldName, String value) {
        try {
            switch (fieldName) {
                // Поля из RegionData
                case "region":
                    this.region = value;
                    break;
                case "regionAddress":
                    this.regionAddress = value;
                    break;

                // Поля из CompetitorData
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
     * Валидирует заполненную сущность.
     * Объединяет логику валидации из RegionData и CompetitorData.
     *
     * @return null, если валидация прошла успешно, иначе - сообщение об ошибке
     */
    @Override
    public String validate() {
        // Проверяем наличие либо региона, либо конкурента для валидности записи
        boolean hasRegionData = region != null && !region.trim().isEmpty();
        boolean hasCompetitorData = competitorName != null && !competitorName.trim().isEmpty();

        if (!hasRegionData && !hasCompetitorData) {
            return "Должен быть указан хотя бы регион или конкурент";
        }

        return null;
    }
}