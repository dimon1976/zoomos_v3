// src/main/java/my/java/model/ExportTemplate.java
package my.java.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import my.java.service.file.options.FileWritingOptions;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "export_templates")
@Data
@Slf4j
public class ExportTemplate {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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

    @Column(name = "options_json", columnDefinition = "TEXT", nullable = false)
    private String optionsJson = "{}";  // Значение по умолчанию - пустой объект

    @Transient
    private FileWritingOptions exportOptions = new FileWritingOptions();

    @Embeddable
    @Data
    public static class ExportField {
        @Column(name = "original_field")
        private String originalField;

        @Column(name = "display_name")
        private String displayName;
    }

    /**
     * Синхронизирует options_json перед сохранением
     */
    @PrePersist
    @PreUpdate
    public void updateTimestampsAndJson() {
        // Обновляем временные метки
        this.updatedAt = ZonedDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = this.updatedAt;
        }

        // Сериализуем параметры экспорта
        try {
            this.optionsJson = objectMapper.writeValueAsString(this.exportOptions);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации параметров экспорта: {}", e.getMessage());
            this.optionsJson = "{}";  // Устанавливаем пустой объект в случае ошибки
        }
    }

    /**
     * Загружает параметры экспорта из JSON при загрузке сущности
     */
    @PostLoad
    public void loadOptionsFromJson() {
        try {
            if (this.optionsJson != null && !this.optionsJson.isEmpty()) {
                this.exportOptions = objectMapper.readValue(this.optionsJson, FileWritingOptions.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Ошибка при десериализации параметров экспорта: {}", e.getMessage());
            this.exportOptions = new FileWritingOptions();  // Создаем новый объект параметров в случае ошибки
        }
    }

    /**
     * Получает тип файла из параметров экспорта
     */
    @Transient
    public String getFileType() {
        return exportOptions != null ? exportOptions.getFileType() : null;
    }

    /**
     * Устанавливает тип файла в параметрах экспорта
     */
    public void setFileType(String fileType) {
        if (exportOptions == null) {
            exportOptions = new FileWritingOptions();
        }
        exportOptions.setFileType(fileType);
    }

    /**
     * Получает FileWritingOptions для экспорта
     */
    public FileWritingOptions getExportOptions() {
        if (this.exportOptions == null) {
            this.exportOptions = new FileWritingOptions();
        }
        return this.exportOptions;
    }

    /**
     * Устанавливает FileWritingOptions для экспорта
     */
    public void setExportOptions(FileWritingOptions options) {
        this.exportOptions = options;
    }
}