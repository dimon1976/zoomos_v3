package by.zoomos.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Сущность Product представляет основную информацию о продукте
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "product_id", unique = true, nullable = false)
    private String productId;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String brand;

    @Column(name = "base_price", precision = 19, scale = 2)
    private BigDecimal basePrice;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RegionData> regionData = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CompetitorData> competitorData = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Добавляет региональные данные к продукту
     *
     * @param regionData региональные данные для добавления
     */
    public void addRegionData(RegionData regionData) {
        this.regionData.add(regionData);
        regionData.setProduct(this);
    }

    /**
     * Добавляет данные конкурента к продукту
     *
     * @param competitorData данные конкурента для добавления
     */
    public void addCompetitorData(CompetitorData competitorData) {
        this.competitorData.add(competitorData);
        competitorData.setProduct(this);
    }
}
