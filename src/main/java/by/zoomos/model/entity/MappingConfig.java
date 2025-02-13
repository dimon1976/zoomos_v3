package by.zoomos.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Сущность для хранения настроек маппинга
 */
@Entity
@Table(name = "mapping_configs")
@Getter
@Setter
@NoArgsConstructor
public class MappingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "file_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private FileType fileType;

    @Column(name = "product_mapping", columnDefinition = "jsonb")
    private String productMapping;

    @Column(name = "region_mapping", columnDefinition = "jsonb")
    private String regionMapping;

    @Column(name = "competitor_mapping", columnDefinition = "jsonb")
    private String competitorMapping;

    private boolean active = true;

    @Column(name = "is_default")
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum FileType {
        XLSX, XLS, CSV
    }
}