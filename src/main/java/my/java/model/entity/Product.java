package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.annotations.FieldDescription;
import my.java.model.enums.DataSourceType;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source")
    private DataSourceType dataSource;

    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long fileId;

    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long clientId;

    @FieldDescription("ID товара")
    private String productId;

    @FieldDescription("Модель")
    @Column(length = 400)
    private String productName;

    @FieldDescription("Бренд")
    private String productBrand;

    @FieldDescription("Штрихкод")
    private String productBar;

    @FieldDescription("Описание")
    private String productDescription;

    @FieldDescription("Ссылка")
    @Column(length = 1100)
    private String productUrl;

    @FieldDescription("Категория товара 1")
    private String productCategory1;
    @FieldDescription("Категория товара 2")
    private String productCategory2;
    @FieldDescription("Категория товара 3")
    private String productCategory3;

    @FieldDescription("Цена")
    private Double productPrice;


    @FieldDescription("Аналог")
    private String productAnalog;

    @FieldDescription("Дополнительное поле 1")
    private String productAdditional1;

    @FieldDescription("Дополнительное поле 2")
    private String productAdditional2;

    @FieldDescription("Дополнительное поле 3")
    private String productAdditional3;

    @FieldDescription("Дополнительное поле 4")
    private String productAdditional4;

    @FieldDescription("Дополнительное поле 5")
    private String productAdditional5;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @FieldDescription(value = "Региональные данные", skipMapping = true)
    private List<RegionData> regionDataList = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @FieldDescription(value = "Данные с сайтов", skipMapping = true)
    private List<CompetitorData> competitorDataList = new ArrayList<>();
}
