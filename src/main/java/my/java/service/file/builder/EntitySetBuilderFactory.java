package my.java.service.file.builder;

import lombok.extern.slf4j.Slf4j;
import my.java.service.file.metadata.EntityFieldService;
import my.java.service.file.transformer.ValueTransformerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Фабрика для создания строителей наборов сущностей.
 */
@Component
@Slf4j
public class EntitySetBuilderFactory {

    private final ValueTransformerFactory transformerFactory;
    private final EntityFieldService entityFieldService;
    private final Map<String, BuilderCreator> builderCreators;

    @Autowired
    public EntitySetBuilderFactory(ValueTransformerFactory transformerFactory, EntityFieldService entityFieldService) {
        this.transformerFactory = transformerFactory;
        this.entityFieldService = entityFieldService;
        this.builderCreators = initializeBuilderCreators();
    }

    /**
     * Инициализирует карту создателей строителей
     *
     * @return карта создателей строителей
     */
    private Map<String, BuilderCreator> initializeBuilderCreators() {
        Map<String, BuilderCreator> creators = new HashMap<>();

        // Регистрируем создателей строителей
        creators.put("product_with_related", this::createProductWithRelatedEntitiesBuilder);

        return creators;
    }

    /**
     * Функциональный интерфейс для создания строителей
     */
    @FunctionalInterface
    private interface BuilderCreator {
        EntitySetBuilder create();
    }

    /**
     * Создает строитель для продукта со связанными сущностями
     *
     * @return строитель продукта со связанными сущностями
     */
    private EntitySetBuilder createProductWithRelatedEntitiesBuilder() {
        return new ProductWithRelatedEntitiesBuilder(transformerFactory, entityFieldService);
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

        String type = entityType.toLowerCase();
        BuilderCreator creator = builderCreators.get(type);

        if (creator != null) {
            log.debug("Создание строителя для типа: {}", type);
            return creator.create();
        }

        log.debug("Строитель для типа {} не найден", type);
        return null;
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

        if (builder == null || params == null) {
            return builder;
        }

        configureBuilder(builder, params, entityType);
        return builder;
    }

    /**
     * Конфигурирует строитель параметрами
     *
     * @param builder строитель для конфигурации
     * @param params параметры конфигурации
     * @param entityType тип сущности
     */
    private void configureBuilder(EntitySetBuilder builder, Map<String, String> params, String entityType) {
        // Применяем маппинги, если поддерживаются билдером
        if (builder instanceof ProductWithRelatedEntitiesBuilder && entityType.equalsIgnoreCase("product_with_related")) {
            ((ProductWithRelatedEntitiesBuilder) builder).withMappings(params);
        }

        // Устанавливаем идентификатор файла, если указан
        if (params.containsKey("fileId")) {
            setFileId(builder, params.get("fileId"));
        }

        // Устанавливаем идентификатор клиента, если указан
        if (params.containsKey("clientId")) {
            setClientId(builder, params.get("clientId"));
        }
    }

    /**
     * Устанавливает идентификатор файла для строителя
     *
     * @param builder строитель
     * @param fileIdStr строковое представление идентификатора файла
     */
    private void setFileId(EntitySetBuilder builder, String fileIdStr) {
        try {
            Long fileId = Long.parseLong(fileIdStr);
            builder.withFileId(fileId);
        } catch (NumberFormatException e) {
            log.warn("Невозможно преобразовать fileId к Long: {}", fileIdStr);
        }
    }

    /**
     * Устанавливает идентификатор клиента для строителя
     *
     * @param builder строитель
     * @param clientIdStr строковое представление идентификатора клиента
     */
    private void setClientId(EntitySetBuilder builder, String clientIdStr) {
        try {
            Long clientId = Long.parseLong(clientIdStr);
            builder.withClientId(clientId);
        } catch (NumberFormatException e) {
            log.warn("Невозможно преобразовать clientId к Long: {}", clientIdStr);
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

        return builderCreators.containsKey(entityType.toLowerCase());
    }
}