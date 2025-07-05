package my.java.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Сущность для хранения деталей сопоставления отдельных полей
 */
@Entity
@Table(name = "field_mapping_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "fieldMapping")
@ToString(exclude = "fieldMapping")
public class FieldMappingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_mapping_id", nullable = false)
    private FieldMapping fieldMapping;

    /**
     * Имя поля в источнике (заголовок CSV)
     */
    @Column(name = "source_field", nullable = false)
    private String sourceField;

    /**
     * Имя поля в целевой сущности
     */
    @Column(name = "target_field", nullable = false)
    private String targetField;

    /**
     * Целевая сущность (для составных шаблонов)
     * Например: PRODUCT, COMPETITOR, REGION
     */
    @Column(name = "target_entity")
    private String targetEntity;

    /**
     * Обязательное поле
     */
    @Column(nullable = false)
    private Boolean required = false;

    /**
     * Тип трансформации (если нужна)
     */
    @Column(name = "transformation_type")
    private String transformationType;

    /**
     * Параметры трансформации (например, формат даты)
     */
    @Column(name = "transformation_params", columnDefinition = "TEXT")
    private String transformationParams;

    /**
     * Правила валидации в JSON формате
     */
    @Column(name = "validation_rules", columnDefinition = "TEXT")
    private String validationRules;

    /**
     * Значение по умолчанию
     */
    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    /**
     * Порядок отображения/обработки
     */
    @Column(name = "order_index")
    private Integer orderIndex = 0;

    /**
     * Получение отображаемого имени целевой сущности
     */
    public String getTargetEntityDisplay() {
        if (targetEntity == null) {
            return "";
        }

        switch (targetEntity) {
            case "PRODUCT":
                return "Товар";
            case "COMPETITOR":
                return "Конкурент";
            case "REGION":
                return "Регион";
            default:
                return targetEntity;
        }
    }

    /**
     * Получение полного имени поля для отображения
     */
    public String getFullTargetFieldName() {
        if (targetEntity != null && !targetEntity.isEmpty()) {
            return String.format("[%s] %s", getTargetEntityDisplay(), targetField);
        }
        return targetField;
    }
}