package my.java.service.file.builder;

import my.java.service.file.transformer.ValueTransformerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Фабрика для создания строителей наборов сущностей.
 */
@Component
public class EntitySetBuilderFactory {

    private final ValueTransformerFactory transformerFactory;

    @Autowired
    public EntitySetBuilderFactory(ValueTransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
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
                return new ProductWithRelatedEntitiesBuilder(transformerFactory);
            // Другие типы строителей могут быть добавлены здесь
            default:
                // По умолчанию возвращаем null, чтобы использовался стандартный механизм импорта
                return null;
        }
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