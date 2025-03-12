package my.java.service.file.metadata;

import my.java.model.entity.CompetitorData;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.service.file.transformer.ValueTransformerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Сервис для получения информации о полях сущностей.
 * Централизует доступ к маппингам полей и их метаданным.
 */
@Service
public class EntityFieldService {

    private final ValueTransformerFactory transformerFactory;

    // Кэш маппингов полей
    private final Map<String, Map<String, String>> fieldMappingsCache = new HashMap<>();

    // Кэш групп полей для составных типов
    private final Map<String, Map<String, List<Map.Entry<String, String>>>> fieldGroupsCache = new HashMap<>();

    @Autowired
    public EntityFieldService(ValueTransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
        initFieldMappings();
    }

    /**
     * Инициализирует кэш маппингов полей.
     */
    private void initFieldMappings() {
        // Инициализируем экземпляры сущностей для получения маппингов
        Product product = new Product();
        product.setTransformerFactory(transformerFactory);

        RegionData regionData = new RegionData();
        regionData.setTransformerFactory(transformerFactory);

        CompetitorData competitorData = new CompetitorData();
        competitorData.setTransformerFactory(transformerFactory);

        // Сохраняем маппинги в кэше
        fieldMappingsCache.put("product", product.getFieldMappings());
        fieldMappingsCache.put("regiondata", regionData.getFieldMappings());
        fieldMappingsCache.put("competitordata", competitorData.getFieldMappings());

        // Инициализируем группы полей для составного типа
        Map<String, List<Map.Entry<String, String>>> productWithRelatedGroups = new LinkedHashMap<>();

        // Создаем списки полей для каждой группы
        List<Map.Entry<String, String>> productFields = new ArrayList<>(product.getFieldMappings().entrySet());
        List<Map.Entry<String, String>> regionFields = new ArrayList<>(regionData.getFieldMappings().entrySet());
        List<Map.Entry<String, String>> competitorFields = new ArrayList<>(competitorData.getFieldMappings().entrySet());

        // Сортируем поля по именам для удобства
        productFields.sort(Map.Entry.comparingByKey());
        regionFields.sort(Map.Entry.comparingByKey());
        competitorFields.sort(Map.Entry.comparingByKey());

        // Добавляем группы полей в кэш
        productWithRelatedGroups.put("Товар", productFields);
        productWithRelatedGroups.put("Регион", regionFields);
        productWithRelatedGroups.put("Конкурент", competitorFields);

        fieldGroupsCache.put("product_with_related", productWithRelatedGroups);
    }

    /**
     * Получает маппинг полей для указанного типа сущности.
     *
     * @param entityType тип сущности
     * @return маппинг полей (ключ - заголовок файла, значение - имя поля сущности)
     */
    public Map<String, String> getFieldMappings(String entityType) {
        if (entityType == null) {
            return Collections.emptyMap();
        }

        return fieldMappingsCache.getOrDefault(entityType.toLowerCase(), Collections.emptyMap());
    }

    /**
     * Получает группы полей для составного типа сущности.
     *
     * @param entityType тип сущности
     * @return карта групп полей (ключ - название группы, значение - список полей)
     */
    public Map<String, List<Map.Entry<String, String>>> getFieldGroups(String entityType) {
        if (entityType == null) {
            return Collections.emptyMap();
        }

        return fieldGroupsCache.getOrDefault(entityType.toLowerCase(), Collections.emptyMap());
    }

    /**
     * Создает экземпляр сущности указанного типа.
     *
     * @param entityType тип сущности
     * @return экземпляр сущности или null, если тип не поддерживается
     */
    public ImportableEntity createEntityInstance(String entityType) {
        if (entityType == null) {
            return null;
        }

        switch (entityType.toLowerCase()) {
            case "product":
                Product product = new Product();
                product.setTransformerFactory(transformerFactory);
                return product;
            case "regiondata":
                RegionData regionData = new RegionData();
                regionData.setTransformerFactory(transformerFactory);
                return regionData;
            case "competitordata":
                CompetitorData competitorData = new CompetitorData();
                competitorData.setTransformerFactory(transformerFactory);
                return competitorData;
            default:
                return null;
        }
    }

    /**
     * Получает список всех поддерживаемых типов сущностей.
     *
     * @return список типов сущностей
     */
    public List<String> getSupportedEntityTypes() {
        List<String> types = new ArrayList<>(fieldMappingsCache.keySet());
        types.add("product_with_related");
        return types;
    }

    /**
     * Получает метаданные полей для всех типов сущностей.
     *
     * @return метаданные полей
     */
    public Map<String, Object> getAllEntityFieldsMetadata() {
        Map<String, Object> metadata = new HashMap<>();

        // Добавляем обычные типы сущностей
        for (Map.Entry<String, Map<String, String>> entry : fieldMappingsCache.entrySet()) {
            metadata.put(entry.getKey(), entry.getValue());
        }

        // Добавляем составные типы
        for (Map.Entry<String, Map<String, List<Map.Entry<String, String>>>> entry : fieldGroupsCache.entrySet()) {
            metadata.put(entry.getKey(), entry.getValue());
        }

        return metadata;
    }
}