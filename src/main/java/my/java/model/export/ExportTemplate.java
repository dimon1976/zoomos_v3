package my.java.model.export;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import my.java.model.Client;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "export_templates")
@Data
@NoArgsConstructor
public class ExportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "format")
    private String format;

    @ElementCollection
    @CollectionTable(name = "export_template_fields",
            joinColumns = @JoinColumn(name = "template_id"))
    @MapKeyColumn(name = "source_field")
    @Column(name = "target_field")
    private Map<String, String> fieldMapping = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "export_template_params",
            joinColumns = @JoinColumn(name = "template_id"))
    @MapKeyColumn(name = "param_name")
    @Column(name = "param_value")
    private Map<String, String> parameters = new HashMap<>();

    @Column(name = "filter_condition")
    private String filterCondition;

    @Column(name = "created_at")
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "last_used_at")
    private ZonedDateTime lastUsedAt;

    public void markAsUsed() {
        this.lastUsedAt = ZonedDateTime.now();
    }
}