package my.java.service.file.builder;

import my.java.service.file.metadata.EntityFieldService;
import my.java.service.file.transformer.ValueTransformerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Фабрика для создания строителей наборов сущностей.
 */
@Component
public class EntitySetBuilderFactory {

    private final ValueTransformerFactory transformerFactory;
    private final EntityFieldService entityFieldService;

    @Autowired
    public EntitySetBuilderFactory(ValueTransformerFactory transformerFactory, EntityFieldService entityFieldService) {
        this.transformerFactory = transformerFactory;
        this.entityFieldService = entityFieldService;
    }

    /**
     * Создает строитель для указанного типа сущностей.
     *
     * @param entityType тип сущности
     * @return подходящий строитель или null, если тип не поддерживается
     */
    public EntitySetBuilder createBuilder(String entityType) {
        if (entityType == null) {
            return null;
        }

        switch (entityType.toLowerCase()) {
            case "product_with_related":
                return new ProductWithRelatedEntitiesBuilder(transformerFactory, entityFieldService);
            // Другие типы строителей могут быть добавлены здесь
            default:
                // По умолчанию возвращаем null, чтобы использовался стандартный механизм импорта
                return null;
        }
    }

    /**
     * Создает строитель и применяет к нему настройки из параметров.
     *
     * @param entityType тип сущности
     * @param params параметры с маппингами и другими настройками
     * @return настроенный строитель или null, если тип не поддерживается
     */
    public EntitySetBuilder createAndConfigureBuilder(String entityType, Map<String, String> params) {
        EntitySetBuilder builder = createBuilder(entityType);

        if (builder != null && params != null) {
            // Применяем маппинги, если поддерживаются билдером
            if (builder instanceof ProductWithRelatedEntitiesBuilder) {
                ((ProductWithRelatedEntitiesBuilder) builder).withMappings(params);
            }

            // Устанавливаем идентификатор файла, если указан
            if (params.containsKey("fileId")) {
                try {
                    Long fileId = Long.parseLong(params.get("fileId"));
                    builder.withFileId(fileId);
                } catch (NumberFormatException e) {
                    // Игнорируем, если значение не числовое
                }
            }
        }

        return builder;
    }

    /**
     * Проверяет, поддерживается ли указанный тип сущности.
     *
     * @param entityType тип сущности
     * @return true, если тип поддерживается
     */
    public boolean supportsEntityType(String entityType) {
        if (entityType == null) {
            return false;
        }

        return entityType.equalsIgnoreCase("product_with_related");
    }
}