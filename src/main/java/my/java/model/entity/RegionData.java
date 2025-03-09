package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.annotations.FieldDescription;

@Setter
@Getter
@Entity
@Table(name = "region_data")
public class RegionData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long id;

    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long clientId;

    @FieldDescription("Город")
    private String region;

    @FieldDescription("Адрес")
    @Column(length = 400)
    private String regionAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Product product;
}
