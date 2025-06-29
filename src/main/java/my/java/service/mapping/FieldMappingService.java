package my.java.service.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.FieldMappingRule;
import my.java.model.FieldMappingTemplate;
import my.java.model.entity.*;
import my.java.repository.FieldMappingTemplateRepository;
import my.java.util.transformer.ValueTransformerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы с шаблонами маппинга полей
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FieldMappingService {

    private final FieldMappingTemplateRepository templateRepository;
    private final ValueTransformerFactory transformerFactory;

    /**
     * Создает автоматический шаблон на основе заголовков CSV и типа сущности
     */
    @Transactional
    public FieldMappingTemplate createAutoMapping(Long clientId, String entityType, List<String> csvHeaders) {
        log.info("Создание автоматического маппинга для {} с {} колонками", entityType, csvHeaders.size());

        // Получаем сущность по типу
        ImportableEntity entity = createEntityInstance(entityType);
        Map<String, String> fieldMappings = entity.getFieldMappings();

        // Создаем шаблон
        FieldMappingTemplate template = new FieldMappingTemplate();
        template.setName("Автоматический шаблон " + entityType + " " + new Date());
        template.setDescription("Автоматически созданный шаблон");
        template.setEntityType(entityType);
        template.setFileFormat("CSV");
        template.setIsActive(true);
        template.setIsDefault(false);
        template.setRules(new ArrayList<>()); // Инициализируем список правил

        // Создаем правила маппинга
        int orderIndex = 0;
        for (String csvHeader : csvHeaders) {
            String entityField = findBestMatch(csvHeader, fieldMappings);
            if (entityField != null) {
                FieldMappingRule rule = FieldMappingRule.builder()
                        .csvHeader(csvHeader)
                        .entityField(entityField)
                        .entityType(determineEntityType(entityType, entityField))
                        .fieldType(determineFieldType(entity.getClass(), entityField))
                        .isRequired(false)
                        .orderIndex(orderIndex++)
                        .isActive(true)
                        .build();

                template.addRule(rule);
            }
        }

        return template;
    }

    /**
     * Применяет шаблон маппинга к данным
     */
    public <T extends ImportableEntity> T applyMapping(
            FieldMappingTemplate template,
            Map<String, String> rowData,
            Class<T> entityClass) throws FileOperationException {

        try {
            T entity = entityClass.getDeclaredConstructor().newInstance();
            entity.setTransformerFactory(transformerFactory);

            Map<String, String> mappedData = new HashMap<>();

            // Применяем правила маппинга
            for (FieldMappingRule rule : template.getRules()) {
                if (!rule.getIsActive()) continue;

                String csvValue = rowData.get(rule.getCsvHeader());

                // Применяем значение по умолчанию если необходимо
                if ((csvValue == null || csvValue.isEmpty()) && rule.getDefaultValue() != null) {
                    csvValue = rule.getDefaultValue();
                }

                // Валидация
                if (rule.getIsRequired() && (csvValue == null || csvValue.isEmpty())) {
                    throw new FileOperationException("Обязательное поле отсутствует: " + rule.getCsvHeader());
                }

                // Трансформация если необходима
                if (csvValue != null && rule.needsTransformation()) {
                    csvValue = transformValue(csvValue, rule);
                }

                mappedData.put(rule.getEntityField(), csvValue);
            }

            // Заполняем сущность
            if (!entity.fillFromMap(mappedData)) {
                throw new FileOperationException("Не удалось заполнить сущность данными");
            }

            // Валидация сущности
            String validationError = entity.validate();
            if (validationError != null) {
                throw new FileOperationException("Ошибка валидации: " + validationError);
            }

            return entity;

        } catch (Exception e) {
            throw new FileOperationException("Ошибка применения маппинга: " + e.getMessage(), e);
        }
    }

    /**
     * Получает доступные шаблоны для клиента
     */
    @Transactional(readOnly = true)
    public List<FieldMappingTemplate> getAvailableTemplates(Long clientId, String entityType) {
        List<FieldMappingTemplate> templates = new ArrayList<>();

        // Шаблоны клиента
        templates.addAll(templateRepository.findActiveByClientIdAndEntityType(clientId, entityType));

        // Шаблон по умолчанию
        templateRepository.findDefaultByEntityType(entityType).ifPresent(templates::add);

        return templates;
    }

    /**
     * Загружает шаблон с правилами
     */
    @Transactional(readOnly = true)
    public Optional<FieldMappingTemplate> getTemplateWithRules(Long templateId) {
        return templateRepository.findByIdWithRules(templateId);
    }

    /**
     * Находит лучшее соответствие заголовка CSV полю сущности
     */
    private String findBestMatch(String csvHeader, Map<String, String> fieldMappings) {
        String normalizedHeader = csvHeader.toLowerCase().trim();

        // Точное совпадение
        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            if (entry.getKey().toLowerCase().equals(normalizedHeader)) {
                return entry.getValue();
            }
        }

        // Частичное совпадение
        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            if (entry.getKey().toLowerCase().contains(normalizedHeader) ||
                    normalizedHeader.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Определяет тип сущности для составных объектов
     */
    private String determineEntityType(String mainEntityType, String fieldName) {
        if ("Product".equals(mainEntityType)) {
            if (fieldName.startsWith("region")) return "Region";
            if (fieldName.startsWith("competitor")) return "Competitor";
        }
        return mainEntityType;
    }

    /**
     * Определяет тип поля по рефлексии
     */
    private String determineFieldType(Class<?> entityClass, String fieldName) {
        try {
            Field field = entityClass.getDeclaredField(fieldName);
            return field.getType().getSimpleName();
        } catch (NoSuchFieldException e) {
            // Пробуем в родительских классах
            Class<?> superClass = entityClass.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                return determineFieldType(superClass, fieldName);
            }
            return "String";
        }
    }

    /**
     * Создает экземпляр сущности по типу
     */
    private ImportableEntity createEntityInstance(String entityType) {
        try {
            switch (entityType) {
                case "Product":
                    return new Product();
                case "Competitor":
                    return new Competitor();
                case "Region":
                    return new Region();
                case "Combined":
                    return new CombinedImport();
                default:
                    String className = "my.java.model.entity." + entityType;
                    Class<?> clazz = Class.forName(className);
                    return (ImportableEntity) clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Неизвестный тип сущности: " + entityType);
        }
    }

    /**
     * Трансформирует значение согласно правилам
     */
    private String transformValue(String value, FieldMappingRule rule) {
        // Здесь можно применить различные трансформации
        // Например: обрезка пробелов, изменение регистра, форматирование
        String transformed = value.trim();

        if (rule.getTransformationParams() != null) {
            // Применяем параметры трансформации
            if (rule.getTransformationParams().contains("uppercase")) {
                transformed = transformed.toUpperCase();
            } else if (rule.getTransformationParams().contains("lowercase")) {
                transformed = transformed.toLowerCase();
            }
        }

        return transformed;
    }
}