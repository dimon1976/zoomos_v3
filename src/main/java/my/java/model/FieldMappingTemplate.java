package my.java.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Шаблон сопоставления полей CSV и сущностей
 */
@Entity
@Table(name = "field_mapping_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"client", "rules"})
@ToString(exclude = {"client", "rules"})
public class FieldMappingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "file_format")
    private String fileFormat;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<FieldMappingRule> rules = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    // Вспомогательные методы
    public void addRule(FieldMappingRule rule) {
        rules.add(rule);
        rule.setTemplate(this);
    }

    public void removeRule(FieldMappingRule rule) {
        rules.remove(rule);
        rule.setTemplate(null);
    }

    /**
     * Находит правило по заголовку CSV
     */
    public FieldMappingRule findRuleByCsvHeader(String csvHeader) {
        return rules.stream()
                .filter(r -> r.getCsvHeader().equalsIgnoreCase(csvHeader))
                .findFirst()
                .orElse(null);
    }

    /**
     * Проверяет, все ли обязательные поля замаплены
     */
    public boolean hasAllRequiredMappings(List<String> csvHeaders) {
        return rules.stream()
                .filter(FieldMappingRule::getIsRequired)
                .allMatch(rule -> csvHeaders.contains(rule.getCsvHeader()));
    }
}