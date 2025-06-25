package my.java.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Правило сопоставления отдельного поля
 */
@Entity
@Table(name = "field_mapping_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "template")
@ToString(exclude = "template")
public class FieldMappingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private FieldMappingTemplate template;

    @Column(name = "csv_header", nullable = false)
    private String csvHeader;

    @Column(name = "entity_field", nullable = false)
    private String entityField;

    @Column(name = "entity_type")
    private String entityType; // Для составных сущностей (Product, Region, Competitor)

    @Column(name = "field_type")
    private String fieldType; // String, Integer, Double, Date и т.д.

    @Column(name = "transformation_params", columnDefinition = "TEXT")
    private String transformationParams; // Параметры для ValueTransformer

    @Column(name = "is_required")
    private Boolean isRequired = false;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "validation_rules", columnDefinition = "TEXT")
    private String validationRules;

    @Column(name = "order_index")
    private Integer orderIndex = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * Проверяет, нужна ли трансформация значения
     */
    public boolean needsTransformation() {
        return transformationParams != null && !transformationParams.isEmpty();
    }

    /**
     * Проверяет, есть ли правила валидации
     */
    public boolean hasValidation() {
        return validationRules != null && !validationRules.isEmpty();
    }
}