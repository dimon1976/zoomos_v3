package my.java.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO для шаблона маппинга полей
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMappingDto {

    private Long id;

    @NotBlank(message = "Название шаблона не может быть пустым")
    @Size(min = 2, max = 255, message = "Название должно содержать от 2 до 255 символов")
    private String name;

    private String description;

    private Long clientId;
    private String clientName;

    @NotBlank(message = "Тип сущности не указан")
    private String entityType;

    @NotBlank(message = "Тип импорта не указан")
    private String importType;

    // Параметры файла
    private String fileEncoding;
    private String csvDelimiter;
    private String csvQuoteChar;

    // Стратегия обработки
    private String duplicateStrategy;

    private Boolean isActive;

    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    // Детали маппинга
    @Builder.Default
    private List<FieldMappingDetailDto> details = new ArrayList<>();

    // Вспомогательные поля для отображения
    private Integer detailsCount;
    private String entityTypeDisplay;
    private String importTypeDisplay;
    private String duplicateStrategyDisplay;

    /**
     * DTO для деталей маппинга
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldMappingDetailDto {
        private Long id;
        private String sourceField;
        private String targetField;
        private String targetEntity;
        private Boolean required;
        private String transformationType;
        private String transformationParams;
        private String defaultValue;
        private Integer orderIndex;

        // Для отображения
        private String targetEntityDisplay;
        private String fullTargetFieldName;
    }
}