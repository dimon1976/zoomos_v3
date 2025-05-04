// src/main/java/my/java/model/ExportTemplate.java
package my.java.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @ElementCollection
    @CollectionTable(name = "export_template_fields",
            joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "display_order")
    private List<ExportField> fields = new ArrayList<>();

    @Column(name = "fields_json", columnDefinition = "TEXT", nullable = false)
    private String fieldsJson = "[]";  // Значение по умолчанию - пустой массив

    @Column(name = "options_json", columnDefinition = "TEXT", nullable = false)
    private String optionsJson = "{}";  // Значение по умолчанию - пустой объект

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
     * Синхронизирует поля fields_json и options_json перед сохранением
     */
    @PrePersist
    @PreUpdate
    public void updateJsonFields() {
        // Сериализуем коллекцию полей
        try {
            this.fieldsJson = objectMapper.writeValueAsString(this.fields);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации полей в JSON: {}", e.getMessage());
            // Устанавливаем пустой массив, чтобы избежать NULL значения
            this.fieldsJson = "[]";
        }

        // Сериализуем параметры файла
        try {
            // Если fileOptions не задан, создаем пустой Map
            Map<String, String> options = (fileOptions != null && !fileOptions.isEmpty()) ?
                    objectMapper.readValue(fileOptions, new TypeReference<Map<String, String>>() {}) :
                    new HashMap<>();

            this.optionsJson = objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации параметров в JSON: {}", e.getMessage());
            // Устанавливаем пустой объект, чтобы избежать NULL значения
            this.optionsJson = "{}";
        }
    }

    /**
     * Загружает поля из JSON при загрузке сущности
     */
    @PostLoad
    public void loadFromJson() {
        // Загрузка полей из fieldsJson
        if (this.fieldsJson != null && !this.fieldsJson.isEmpty()) {
            try {
                List<ExportField> loadedFields = objectMapper.readValue(
                        this.fieldsJson, new TypeReference<List<ExportField>>() {});

                // Обновляем коллекцию только если загрузились поля
                if (loadedFields != null && !loadedFields.isEmpty()) {
                    this.fields.clear();
                    this.fields.addAll(loadedFields);
                }
            } catch (JsonProcessingException e) {
                log.error("Ошибка при десериализации JSON в поля: {}", e.getMessage());
            }
        }

        // Загрузка параметров из optionsJson в fileOptions
        if (this.optionsJson != null && !this.optionsJson.isEmpty() &&
                (this.fileOptions == null || this.fileOptions.isEmpty())) {
            try {
                this.fileOptions = this.optionsJson;
            } catch (Exception e) {
                log.error("Ошибка при обработке options_json: {}", e.getMessage());
            }
        }
    }

    /**
     * Возвращает параметры экспорта файла в виде Map
     */
    public Map<String, String> getFileOptionsAsMap() {
        String jsonToUse = fileOptions != null && !fileOptions.isEmpty() ? fileOptions : optionsJson;

        if (jsonToUse == null || jsonToUse.isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(jsonToUse, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Ошибка при чтении параметров файла: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}