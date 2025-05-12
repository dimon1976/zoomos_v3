// src/main/java/my/java/model/ExportTemplate.java
package my.java.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import my.java.service.file.options.FileWritingOptions;

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

    @ElementCollection
    @CollectionTable(name = "export_template_fields",
            joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "display_order")
    private List<ExportField> fields = new ArrayList<>();

    /**
     * -- GETTER --
     *  Получает JSON-представление полей
     */
    @Getter
    @Column(name = "fields_json", columnDefinition = "TEXT", nullable = false)
    private String fieldsJson = "[]";  // Значение по умолчанию - пустой массив

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

    // Временное поле для обратной совместимости
    @Column(name = "file_options", columnDefinition = "TEXT")
    private String fileOptions;

    // Параметры форматирования в виде объекта
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
     * Синхронизирует поля fields_json и options_json перед сохранением
     */
    @PrePersist
    @PreUpdate
    public void updateJsonFields() {
        // Сериализуем коллекцию полей
        try {
            log.debug("updateJsonFields: сериализация коллекции полей, размер: {}", this.fields != null ? this.fields.size() : 0);
            this.fieldsJson = objectMapper.writeValueAsString(this.fields);
            log.debug("updateJsonFields: поля сериализованы в JSON: {}", this.fieldsJson);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации полей в JSON: {}", e.getMessage());
            // Устанавливаем пустой массив, чтобы избежать NULL значения
            this.fieldsJson = "[]";
        }

        // Сериализуем параметры экспорта
        try {
            this.optionsJson = objectMapper.writeValueAsString(exportOptions);
            // Заполняем fileOptions для обратной совместимости
            this.fileOptions = this.optionsJson;
            log.debug("updateJsonFields: параметры экспорта сериализованы в JSON: {}", this.optionsJson);
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
        log.debug("loadFromJson: начало загрузки полей из JSON: {}", this.fieldsJson);

        // Загрузка полей из fieldsJson
        if (this.fieldsJson != null && !this.fieldsJson.isEmpty()) {
            log.debug("loadFromJson: fieldsJson не пустой, начинаем десериализацию");
            try {
                List<ExportField> loadedFields = objectMapper.readValue(
                        this.fieldsJson, new TypeReference<List<ExportField>>() {});

                log.debug("loadFromJson: десериализовано полей: {}", loadedFields != null ? loadedFields.size() : 0);

                // Обновляем коллекцию только если загрузились поля
                if (loadedFields != null && !loadedFields.isEmpty()) {
                    this.fields.clear();
                    this.fields.addAll(loadedFields);
                    log.debug("loadFromJson: обновлена коллекция полей, новый размер: {}", this.fields.size());
                } else {
                    log.warn("loadFromJson: после десериализации получен пустой список полей");
                }
            } catch (JsonProcessingException e) {
                log.error("Ошибка при десериализации JSON в поля: {}", e.getMessage());
            }
        } else {
            log.warn("loadFromJson: fieldsJson пустой или null, невозможно загрузить поля");
        }

        // Загрузка параметров из optionsJson
        if (this.optionsJson != null && !this.optionsJson.isEmpty()) {
            try {
                // Пытаемся загрузить как FileWritingOptions
                this.exportOptions = objectMapper.readValue(this.optionsJson, FileWritingOptions.class);
                log.debug("loadFromJson: параметры успешно загружены из optionsJson");
            } catch (Exception e) {
                log.warn("Не удалось десериализовать optionsJson в FileWritingOptions: {}", e.getMessage());

                // Пытаемся загрузить как Map и создать FileWritingOptions
                try {
                    Map<String, String> optionsMap = objectMapper.readValue(
                            this.optionsJson, new TypeReference<Map<String, String>>() {});
                    this.exportOptions = FileWritingOptions.fromMap(optionsMap);
                    log.debug("loadFromJson: параметры загружены из optionsJson через Map");
                } catch (Exception ex) {
                    log.error("Ошибка при создании FileWritingOptions из Map: {}", ex.getMessage());
                    this.exportOptions = new FileWritingOptions();
                }
            }
        } else if (this.fileOptions != null && !this.fileOptions.isEmpty()) {
            // Пробуем использовать fileOptions для обратной совместимости
            try {
                Map<String, String> optionsMap = objectMapper.readValue(
                        this.fileOptions, new TypeReference<Map<String, String>>() {});
                this.exportOptions = FileWritingOptions.fromMap(optionsMap);
                log.debug("loadFromJson: параметры загружены из fileOptions");
            } catch (Exception e) {
                log.error("Ошибка при создании FileWritingOptions из fileOptions: {}", e.getMessage());
                this.exportOptions = new FileWritingOptions();
            }
        }

        log.debug("loadFromJson: завершено, fields.size={}", this.fields.size());
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

        // Обновляем JSON для сохранения
        try {
            this.optionsJson = objectMapper.writeValueAsString(options);
            this.fileOptions = this.optionsJson; // для обратной совместимости
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации FileWritingOptions: {}", e.getMessage());
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
     * Устанавливает список полей шаблона
     */
    public void setFields(List<ExportField> fields) {
        this.fields = fields;

        // Обновляем JSON-представление при изменении списка полей
        try {
            this.fieldsJson = objectMapper.writeValueAsString(fields);
            log.debug("setFields: автоматически обновлен fieldsJson: {}", this.fieldsJson);
        } catch (JsonProcessingException e) {
            log.error("setFields: ошибка при сериализации полей: {}", e.getMessage());
            this.fieldsJson = "[]";
        }
    }
}