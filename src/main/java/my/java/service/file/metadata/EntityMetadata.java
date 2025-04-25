// src/main/java/my/java/service/file/metadata/EntityMetadata.java
package my.java.service.file.metadata;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Класс для хранения метаданных о сущности
 */
@Getter
@Setter
public class EntityMetadata {
    private String entityType;
    private Class<?> entityClass;
    private String displayName;
    private boolean isMainEntity;
    private Map<String, FieldMetadata> fields = new LinkedHashMap<>();
    private List<RelationshipMetadata> relationships = new ArrayList<>();

    public EntityMetadata(String entityType, Class<?> entityClass, String displayName) {
        this.entityType = entityType;
        this.entityClass = entityClass;
        this.displayName = displayName;
    }

    /**
     * Добавляет поле к метаданным сущности
     */
    public void addField(String fieldName, String displayName, Class<?> type, boolean required, boolean exportable) {
        fields.put(fieldName, FieldMetadata.builder()
                .name(fieldName)
                .displayName(displayName)
                .type(type)
                .required(required)
                .exportable(exportable)
                .build());
    }

    /**
     * Добавляет связь к метаданным сущности
     */
    public void addRelationship(String relatedEntityType, String relationshipField, RelationshipType type) {
        relationships.add(RelationshipMetadata.builder()
                .relatedEntityType(relatedEntityType)
                .relationshipField(relationshipField)
                .type(type)
                .build());
    }

    /**
     * Получает все поля с префиксом сущности
     */
    public Map<String, FieldMetadata> getPrefixedFields() {
        return fields.entrySet().stream()
                .collect(HashMap::new,
                        (map, entry) -> map.put(entityType + "." + entry.getKey(), entry.getValue()),
                        HashMap::putAll);
    }

    /**
     * Метаданные о поле сущности
     */
    @Data
    @Builder
    public static class FieldMetadata {
        private String name;
        private String displayName;
        private Class<?> type;
        private boolean required;
        private boolean exportable;
        private String defaultValue;
        private String validationPattern;
    }

    /**
     * Метаданные о связи между сущностями
     */
    @Data
    @Builder
    public static class RelationshipMetadata {
        private String relatedEntityType;
        private String relationshipField;
        private RelationshipType type;
    }

    /**
     * Типы связей между сущностями
     */
    public enum RelationshipType {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }
}