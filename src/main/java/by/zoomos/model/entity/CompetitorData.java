package by.zoomos.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность CompetitorData представляет информацию о ценах конкурентов на продукт
 */
@Entity
@Table(name = "competitor_data")
@Data
@NoArgsConstructor
public class CompetitorData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "competitor_name", nullable = false)
    private String competitorName;

    @Column(name = "competitor_url")
    private String competitorUrl;

    @Column(name = "competitor_price", precision = 19, scale = 2)
    private BigDecimal competitorPrice;

    @Column(name = "competitor_promo_price", precision = 19, scale = 2)
    private BigDecimal competitorPromoPrice;

    @Column(name = "parsed_at")
    private LocalDateTime parsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Создает новый объект данных конкурента
     *
     * @param competitorName название конкурента
     * @param competitorUrl URL страницы конкурента
     * @param competitorPrice цена у конкурента
     * @param competitorPromoPrice промо-цена у конкурента
     * @return новый объект CompetitorData
     */
    public static CompetitorData of(String competitorName, String competitorUrl,
                                    BigDecimal competitorPrice, BigDecimal competitorPromoPrice) {
        CompetitorData competitorData = new CompetitorData();
        competitorData.setCompetitorName(competitorName);
        competitorData.setCompetitorUrl(competitorUrl);
        competitorData.setCompetitorPrice(competitorPrice);
        competitorData.setCompetitorPromoPrice(competitorPromoPrice);
        competitorData.setParsedAt(LocalDateTime.now());
        return competitorData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompetitorData)) return false;
        CompetitorData that = (CompetitorData) o;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
