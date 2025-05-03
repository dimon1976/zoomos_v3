// src/main/java/my/java/model/ExportTemplate.java
package my.java.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "export_templates")
@Data
public class ExportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @ElementCollection
    @CollectionTable(name = "export_template_fields",
            joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "display_order")
    private List<ExportField> fields = new ArrayList<>();

    @Column(name = "strategy_id")
    private String strategyId;

    @Column(name = "is_default")
    private boolean isDefault;

    @Column(name = "created_at")
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @Column(name = "file_options", columnDefinition = "TEXT")
    private String fileOptions;

    @Embeddable
    @Data
    public static class ExportField {
        @Column(name = "original_field")
        private String originalField;

        @Column(name = "display_name")
        private String displayName;
    }

    /**
     * Возвращает параметры экспорта файла в виде Map
     */
    public Map<String, String> getFileOptionsAsMap() {
        return fileOptions != null ?
                Map.of() : // Здесь будет парсинг JSON
                Map.of();
    }
}