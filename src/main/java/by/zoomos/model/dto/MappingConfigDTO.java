package by.zoomos.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO для работы с настройками маппинга
 */
@Data
@NoArgsConstructor
public class MappingConfigDTO {
    private Long id;
    private Long clientId;
    private String name;
    private String description;
    private String fileType;
    private boolean isDefault;
    private boolean active;

    private Map<String, String> productMapping;
    private Map<String, String> regionMapping;
    private Map<String, String> competitorMapping;

    @Data
    @NoArgsConstructor
    public static class FieldMapping {
        private String sourceField;
        private String targetField;
        private boolean required;
        private String defaultValue;
        private String transformation;
    }
}