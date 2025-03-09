package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.annotations.FieldDescription;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "competitor_data")
public class CompetitorData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long id;

    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long clientId;

    @FieldDescription("Сайт")
    @Column(length = 400)
    private String competitorName;

    @FieldDescription("Цена конкурента")
    private String competitorPrice;

    @FieldDescription("Акционная цена")
    private String competitorPromotionalPrice;

    @FieldDescription("Время")
    private String competitorTime;

    @FieldDescription("Дата")
    private String competitorDate;

    @FieldDescription("Дата:Время")
    private LocalDateTime competitorLocalDateTime;

    @FieldDescription("Статус")
    private String competitorStockStatus;

    @FieldDescription("Дополнительная цена конкурента")
    private String competitorAdditionalPrice;

    @FieldDescription("Комментарий")
    @Column(length = 1000)
    private String competitorCommentary;

    @FieldDescription("Наименование товара конкурента")
    @Column(length = 400)
    private String competitorProductName;

    @FieldDescription("Дополнительное поле")
    private String competitorAdditional;

    @FieldDescription("Дополнительное поле 2")
    private String competitorAdditional2;

    @FieldDescription("Ссылка")
    @Column(length = 1200)
    private String competitorUrl;

    @FieldDescription("Скриншот")
    @Column(length = 1200)
    private String competitorWebCacheUrl;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Product product;
}
