package my.java.service.file.metadata;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Реестр всех сущностей и их связей для импорта/экспорта
 */
@Component
@Slf4j
public class EntityRegistry {
    private final Map<String, EntityMetadata> entities = new HashMap<>();
    private final Map<String, Class<? extends ImportableEntity>> entityClasses = new HashMap<>();
    private final Map<String, String> mainEntityByRelated = new HashMap<>();

    @PostConstruct
    public void init() {
        // Регистрация сущности Product
        registerEntity("product", Product.class, "Товар", true);
        addProductFields();

        // Регистрация сущности Region
        registerEntity("region", Region.class, "Регион", false);
        addRegionFields();
        addRelationship("region", "product", "product", EntityMetadata.RelationshipType.MANY_TO_ONE);

        // Регистрация сущности Competitor
        registerEntity("competitor", Competitor.class, "Конкурент", false);
        addCompetitorFields();
        addRelationship("competitor", "product", "product", EntityMetadata.RelationshipType.MANY_TO_ONE);

        log.info("Зарегистрировано {} сущностей", entities.size());
    }

    /**
     * Регистрация новой сущности
     */
    public void registerEntity(String entityType, Class<? extends ImportableEntity> entityClass,
                               String displayName, boolean isMainEntity) {
        EntityMetadata metadata = new EntityMetadata(entityType, entityClass, displayName);
        metadata.setMainEntity(isMainEntity);
        entities.put(entityType, metadata);
        entityClasses.put(entityType, entityClass);

        log.debug("Зарегистрирована сущность: {}, класс: {}", entityType, entityClass.getSimpleName());
    }

    /**
     * Добавление связи между сущностями
     */
    public void addRelationship(String sourceEntityType, String targetEntityType,
                                String relationshipField, EntityMetadata.RelationshipType type) {
        EntityMetadata sourceMetadata = entities.get(sourceEntityType);
        if (sourceMetadata != null) {
            sourceMetadata.addRelationship(targetEntityType, relationshipField, type);

            // Если целевая сущность является основной, добавляем маппинг для связанной сущности
            EntityMetadata targetMetadata = entities.get(targetEntityType);
            if (targetMetadata != null && targetMetadata.isMainEntity()) {
                mainEntityByRelated.put(sourceEntityType, targetEntityType);
            }

            log.debug("Добавлена связь: {} -> {} (поле: {})",
                    sourceEntityType, targetEntityType, relationshipField);
        }
    }

    /**
     * Получение метаданных сущности
     */
    public EntityMetadata getEntityMetadata(String entityType) {
        return entities.get(entityType);
    }

    /**
     * Получение класса сущности
     */
    public Class<? extends ImportableEntity> getEntityClass(String entityType) {
        return entityClasses.get(entityType);
    }

    /**
     * Получение основной сущности для связанной сущности
     */
    public String getMainEntityForRelated(String relatedEntityType) {
        return mainEntityByRelated.get(relatedEntityType);
    }

    /**
     * Получение всех зарегистрированных сущностей
     */
    public Collection<EntityMetadata> getAllEntities() {
        return entities.values();
    }

    /**
     * Получение всех основных сущностей
     */
    public List<EntityMetadata> getMainEntities() {
        return entities.values().stream()
                .filter(EntityMetadata::isMainEntity)
                .toList();
    }

    /**
     * Получение всех связанных сущностей для основной сущности
     */
    public List<EntityMetadata> getRelatedEntities(String mainEntityType) {
        List<EntityMetadata> related = new ArrayList<>();

        for (Map.Entry<String, String> entry : mainEntityByRelated.entrySet()) {
            if (entry.getValue().equals(mainEntityType)) {
                related.add(entities.get(entry.getKey()));
            }
        }

        return related;
    }

    /**
     * Получение всех полей всех сущностей с префиксами
     */
    public Map<String, EntityMetadata.FieldMetadata> getAllPrefixedFields() {
        Map<String, EntityMetadata.FieldMetadata> allFields = new LinkedHashMap<>();

        entities.values().forEach(metadata -> {
            allFields.putAll(metadata.getPrefixedFields());
        });

        return allFields;
    }

    /**
     * Получение всех полей конкретной сущности с префиксами
     */
    public Map<String, EntityMetadata.FieldMetadata> getPrefixedFieldsForEntity(String entityType) {
        EntityMetadata metadata = entities.get(entityType);
        if (metadata != null) {
            return metadata.getPrefixedFields();
        }
        return Collections.emptyMap();
    }

    /**
     * Получение всех полей для составной сущности (основная + связанные)
     */
    public Map<String, EntityMetadata.FieldMetadata> getCompositeEntityFields(String mainEntityType) {
        Map<String, EntityMetadata.FieldMetadata> fields = new LinkedHashMap<>();

        // Добавляем поля основной сущности
        EntityMetadata mainMetadata = entities.get(mainEntityType);
        if (mainMetadata != null) {
            fields.putAll(mainMetadata.getPrefixedFields());

            // Добавляем поля связанных сущностей
            getRelatedEntities(mainEntityType).forEach(relatedMetadata -> {
                fields.putAll(relatedMetadata.getPrefixedFields());
            });
        }

        return fields;
    }

    // Методы для добавления полей для каждой сущности
    private void addProductFields() {
        EntityMetadata metadata = entities.get("product");
        metadata.addField("productId", "ID товара", String.class, true, true);
        metadata.addField("productName", "Название товара", String.class, true, true);
        metadata.addField("productBrand", "Бренд", String.class, false, true);
        metadata.addField("productBar", "Штрихкод", String.class, false, true);
        metadata.addField("productDescription", "Описание", String.class, false, true);
        metadata.addField("productUrl", "Ссылка", String.class, false, true);
        metadata.addField("productCategory1", "Категория 1", String.class, false, true);
        metadata.addField("productCategory2", "Категория 2", String.class, false, true);
        metadata.addField("productCategory3", "Категория 3", String.class, false, true);
        metadata.addField("productPrice", "Цена", Double.class, false, true);
        metadata.addField("productAnalog", "Аналог", String.class, false, true);
        metadata.addField("productAdditional1", "Доп. поле 1", String.class, false, true);
        metadata.addField("productAdditional2", "Доп. поле 2", String.class, false, true);
        metadata.addField("productAdditional3", "Доп. поле 3", String.class, false, true);
        metadata.addField("productAdditional4", "Доп. поле 4", String.class, false, true);
        metadata.addField("productAdditional5", "Доп. поле 5", String.class, false, true);
    }

    private void addRegionFields() {
        EntityMetadata metadata = entities.get("region");
        metadata.addField("region", "Город", String.class, true, true);
        metadata.addField("regionAddress", "Адрес", String.class, false, true);
    }

    private void addCompetitorFields() {
        EntityMetadata metadata = entities.get("competitor");
        metadata.addField("competitorName", "Сайт", String.class, true, true);
        metadata.addField("competitorPrice", "Цена конкурента", String.class, false, true);
        metadata.addField("competitorPromotionalPrice", "Акционная цена", String.class, false, true);
        metadata.addField("competitorTime", "Время", String.class, false, true);
        metadata.addField("competitorDate", "Дата", String.class, false, true);
        metadata.addField("competitorLocalDateTime", "Дата:Время", java.time.LocalDateTime.class, false, true);
        metadata.addField("competitorStockStatus", "Статус", String.class, false, true);
        metadata.addField("competitorAdditionalPrice", "Дополнительная цена", String.class, false, true);
        metadata.addField("competitorCommentary", "Комментарий", String.class, false, true);
        metadata.addField("competitorProductName", "Наименование товара конкурента", String.class, false, true);
        metadata.addField("competitorAdditional", "Дополнительное поле", String.class, false, true);
        metadata.addField("competitorAdditional2", "Дополнительное поле 2", String.class, false, true);
        metadata.addField("competitorUrl", "Ссылка", String.class, false, true);
        metadata.addField("competitorWebCacheUrl", "Скриншот", String.class, false, true);
    }
}