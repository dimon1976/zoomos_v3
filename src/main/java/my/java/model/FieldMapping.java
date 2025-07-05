package my.java.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность для хранения шаблонов сопоставления полей
 */
@Entity
@Table(name = "field_mappings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"details", "client"})
@ToString(exclude = {"details", "client"})
public class FieldMapping {

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

    /**
     * Тип сущности или составной тип (COMBINED, PRODUCT, COMPETITOR, REGION)
     */
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    /**
     * Тип импорта: COMBINED (составной) или SINGLE (отдельная сущность)
     */
    @Column(name = "import_type", nullable = false)
    private String importType = "COMBINED";

    /**
     * Параметры файла
     */
    @Column(name = "file_encoding")
    private String fileEncoding = "UTF-8";

    @Column(name = "csv_delimiter")
    private String csvDelimiter = ";";

    @Column(name = "csv_quote_char")
    private String csvQuoteChar = "\"";

    /**
     * Стратегия обработки дубликатов: SKIP, OVERRIDE, ERROR
     */
    @Column(name = "duplicate_strategy")
    private String duplicateStrategy = "SKIP";

    /**
     * Дополнительные параметры в JSON формате
     */
    @Column(name = "additional_params", columnDefinition = "TEXT")
    private String additionalParams;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    /**
     * Детали маппинга полей
     */
    @OneToMany(mappedBy = "fieldMapping", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<FieldMappingDetail> details = new ArrayList<>();

    /**
     * Вспомогательные методы
     */
    public void addDetail(FieldMappingDetail detail) {
        details.add(detail);
        detail.setFieldMapping(this);
    }

    public void removeDetail(FieldMappingDetail detail) {
        details.remove(detail);
        detail.setFieldMapping(null);
    }

    /**
     * Проверка, является ли шаблон составным
     */
    public boolean isCombined() {
        return "COMBINED".equals(importType);
    }

    /**
     * Получение отображаемого имени типа импорта
     */
    public String getImportTypeDisplay() {
        return "COMBINED".equals(importType) ? "Составной" : "Отдельная сущность";
    }

    /**
     * Получение отображаемого имени типа сущности
     */
    public String getEntityTypeDisplay() {
        switch (entityType) {
            case "COMBINED":
                return "Составные данные";
            case "PRODUCT":
                return "Товары";
            case "COMPETITOR":
                return "Конкуренты";
            case "REGION":
                return "Регионы";
            default:
                return entityType;
        }
    }

    /**
     * Получение отображаемого имени стратегии дубликатов
     */
    public String getDuplicateStrategyDisplay() {
        switch (duplicateStrategy) {
            case "SKIP":
                return "Пропускать";
            case "OVERRIDE":
                return "Перезаписывать";
            case "ERROR":
                return "Ошибка";
            default:
                return duplicateStrategy;
        }
    }
}