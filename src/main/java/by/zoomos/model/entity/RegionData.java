package by.zoomos.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность RegionData представляет информацию о продукте в конкретном регионе
 */
@Entity
@Table(name = "region_data")
@Getter
@Setter
@NoArgsConstructor
public class RegionData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String region;

    @Column(name = "regional_price", precision = 19, scale = 2)
    private BigDecimal regionalPrice;

    @Column(name = "stock_amount")
    private Integer stockAmount;

    private String warehouse;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Создает новый объект региональных данных
     *
     * @param region регион
     * @param regionalPrice цена в регионе
     * @param stockAmount количество на складе
     * @param warehouse склад
     * @return новый объект RegionData
     */
    public static RegionData of(String region, BigDecimal regionalPrice, Integer stockAmount, String warehouse) {
        RegionData regionData = new RegionData();
        regionData.setRegion(region);
        regionData.setRegionalPrice(regionalPrice);
        regionData.setStockAmount(stockAmount);
        regionData.setWarehouse(warehouse);
        return regionData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegionData)) return false;
        RegionData that = (RegionData) o;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
