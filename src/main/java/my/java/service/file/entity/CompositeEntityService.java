package my.java.service.file.entity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.service.entity.competitor.CompetitorService;
import my.java.service.entity.product.ProductService;
import my.java.service.entity.region.RegionService;
import my.java.service.file.metadata.EntityMetadata;
import my.java.service.file.metadata.EntityRegistry;
import my.java.service.file.transformer.ValueTransformerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Сервис для работы с составными сущностями при импорте и экспорте
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompositeEntityService {

    private final EntityRegistry entityRegistry;
    private final ProductService productService;
    private final RegionService regionService;
    private final CompetitorService competitorService;
    private final ValueTransformerFactory transformerFactory;

    // Кэш для хранения созданных сущностей Product по их внешним идентификаторам
    private final Map<String, Product> productCache = new ConcurrentHashMap<>();

    /**
     * Обработка строки данных из файла для импорта в составные сущности
     *
     * @param data карта данных из файла (ключ - заголовок, значение - строковое значение)
     * @param mappedFields карта маппинга полей (ключ - заголовок файла, значение - поле сущности с префиксом)
     * @param clientId идентификатор клиента
     * @return список созданных сущностей
     */
    @Transactional
    public List<ImportableEntity> processRow(Map<String, String> data, Map<String, String> mappedFields,
                                             Long clientId) {
        log.debug("Обработка строки данных для импорта в составные сущности");

        // Подготавливаем карты данных для каждой сущности
        Map<String, Map<String, String>> entityData = prepareEntityData(data, mappedFields);

        List<ImportableEntity> resultEntities = new ArrayList<>();

        // Сначала обрабатываем основную сущность (Product)
        if (entityData.containsKey("product")) {
            Product product = processProductData(entityData.get("product"), clientId);
            if (product != null) {
                resultEntities.add(product);

                // Затем обрабатываем связанные сущности
                if (entityData.containsKey("region")) {
                    Region region = processRegionData(entityData.get("region"), product, clientId);
                    if (region != null) {
                        resultEntities.add(region);
                    }
                }

                if (entityData.containsKey("competitor")) {
                    Competitor competitor = processCompetitorData(entityData.get("competitor"), product, clientId);
                    if (competitor != null) {
                        resultEntities.add(competitor);
                    }
                }
            }
        }

        return resultEntities;
    }

    /**
     * Подготовка данных для каждой сущности из общей карты данных
     *
     * @param data карта данных из файла
     * @param mappedFields карта маппинга полей
     * @return карта данных для каждой сущности
     */
    private Map<String, Map<String, String>> prepareEntityData(Map<String, String> data,
                                                               Map<String, String> mappedFields) {
        Map<String, Map<String, String>> result = new HashMap<>();

        // Преобразуем маппинг полей для удобства работы
        Map<String, String> headerToField = new HashMap<>();
        for (Map.Entry<String, String> entry : mappedFields.entrySet()) {
            headerToField.put(entry.getKey(), entry.getValue());
        }

        // Обрабатываем каждый заголовок из файла
        for (Map.Entry<String, String> dataEntry : data.entrySet()) {
            String header = dataEntry.getKey();
            String value = dataEntry.getValue();

            // Получаем соответствующее поле с префиксом сущности
            String mappedField = headerToField.get(header);
            if (mappedField != null) {
                // Разделяем префикс сущности и имя поля
                String[] parts = mappedField.split("\\.", 2);
                if (parts.length == 2) {
                    String entityType = parts[0];
                    String fieldName = parts[1];

                    // Добавляем данные в соответствующую карту сущности
                    result.computeIfAbsent(entityType, k -> new HashMap<>())
                            .put(fieldName, value);
                }
            }
        }

        return result;
    }

    /**
     * Обработка данных для сущности Product
     *
     * @param data данные для продукта
     * @param clientId идентификатор клиента
     * @return созданный или обновленный продукт
     */
    private Product processProductData(Map<String, String> data, Long clientId) {
        try {
            Product product = new Product();
            product.setTransformerFactory(transformerFactory);
            product.setClientId(clientId);

            // Заполняем поля продукта
            boolean filled = product.fillFromMap(data);
            if (!filled) {
                log.error("Не удалось заполнить сущность Product данными");
                return null;
            }

            // Валидируем сущность
            String validationError = product.validate();
            if (validationError != null) {
                log.error("Ошибка валидации Product: {}", validationError);
                return null;
            }

            // Проверяем существование продукта по его внешнему идентификатору
            if (product.getProductId() != null) {
                // Проверяем кэш
                Product cachedProduct = productCache.get(clientId + "_" + product.getProductId());
                if (cachedProduct != null) {
                    log.debug("Найден продукт в кэше: {}", product.getProductId());
                    return cachedProduct;
                }

                // Проверяем БД
                Optional<Product> existingProduct = productService.findByProductIdAndClientId(
                        product.getProductId(), clientId);

                if (existingProduct.isPresent()) {
                    Product existing = existingProduct.get();

                    // Обновляем поля существующего продукта
                    existing.setProductName(product.getProductName());
                    existing.setProductBrand(product.getProductBrand());
                    existing.setProductBar(product.getProductBar());
                    existing.setProductDescription(product.getProductDescription());
                    existing.setProductUrl(product.getProductUrl());
                    existing.setProductCategory1(product.getProductCategory1());
                    existing.setProductCategory2(product.getProductCategory2());
                    existing.setProductCategory3(product.getProductCategory3());
                    existing.setProductPrice(product.getProductPrice());
                    existing.setProductAnalog(product.getProductAnalog());
                    existing.setProductAdditional1(product.getProductAdditional1());
                    existing.setProductAdditional2(product.getProductAdditional2());
                    existing.setProductAdditional3(product.getProductAdditional3());
                    existing.setProductAdditional4(product.getProductAdditional4());
                    existing.setProductAdditional5(product.getProductAdditional5());

                    // Сохраняем обновленный продукт
                    Product savedProduct = productService.saveProduct(existing);

                    // Добавляем в кэш
                    productCache.put(clientId + "_" + savedProduct.getProductId(), savedProduct);

                    log.debug("Обновлен существующий продукт: {}", savedProduct.getProductId());
                    return savedProduct;
                }
            }

            // Создаем новый продукт
            Product savedProduct = productService.saveProduct(product);

            // Добавляем в кэш, если есть идентификатор
            if (savedProduct.getProductId() != null) {
                productCache.put(clientId + "_" + savedProduct.getProductId(), savedProduct);
            }

            log.debug("Создан новый продукт: {}", savedProduct.getId());
            return savedProduct;

        } catch (Exception e) {
            log.error("Ошибка при обработке данных Product: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Обработка данных для сущности Region
     *
     * @param data данные для региона
     * @param product связанный продукт
     * @param clientId идентификатор клиента
     * @return созданный или обновленный регион
     */
    private Region processRegionData(Map<String, String> data, Product product, Long clientId) {
        try {
            // Проверяем, содержит ли карта данных достаточно информации
            if (data.isEmpty() || !data.containsKey("region")) {
                log.debug("Недостаточно данных для региона");
                return null;
            }

            Region region = new Region();
            region.setTransformerFactory(transformerFactory);
            region.setClientId(clientId);
            region.setProduct(product);

            // Заполняем поля региона
            boolean filled = region.fillFromMap(data);
            if (!filled) {
                log.error("Не удалось заполнить сущность Region данными");
                return null;
            }

            // Валидируем сущность
            String validationError = region.validate();
            if (validationError != null) {
                log.error("Ошибка валидации Region: {}", validationError);
                return null;
            }

            // Проверяем существование региона
            Optional<Region> existingRegion = regionService.findByRegionAndProductId(
                    region.getRegion(), product.getId());

            if (existingRegion.isPresent()) {
                Region existing = existingRegion.get();

                // Обновляем поля существующего региона
                existing.setRegionAddress(region.getRegionAddress());

                // Сохраняем обновленный регион
                Region savedRegion = regionService.saveRegion(existing);

                log.debug("Обновлен существующий регион: {}", savedRegion.getRegion());
                return savedRegion;
            }

            // Создаем новый регион
            Region savedRegion = regionService.saveRegion(region);

            log.debug("Создан новый регион: {}", savedRegion.getId());
            return savedRegion;

        } catch (Exception e) {
            log.error("Ошибка при обработке данных Region: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Обработка данных для сущности Competitor
     *
     * @param data данные для конкурента
     * @param product связанный продукт
     * @param clientId идентификатор клиента
     * @return созданный или обновленный конкурент
     */
    private Competitor processCompetitorData(Map<String, String> data, Product product, Long clientId) {
        try {
            // Проверяем, содержит ли карта данных достаточно информации
            if (data.isEmpty() || !data.containsKey("competitorName")) {
                log.debug("Недостаточно данных для конкурента");
                return null;
            }

            Competitor competitor = new Competitor();
            competitor.setTransformerFactory(transformerFactory);
            competitor.setClientId(clientId);
            competitor.setProduct(product);

            // Заполняем поля конкурента
            boolean filled = competitor.fillFromMap(data);
            if (!filled) {
                log.error("Не удалось заполнить сущность Competitor данными");
                return null;
            }

            // Валидируем сущность
            String validationError = competitor.validate();
            if (validationError != null) {
                log.error("Ошибка валидации Competitor: {}", validationError);
                return null;
            }

            // Проверяем существование конкурента
            Optional<Competitor> existingCompetitor = competitorService.findByCompetitorNameAndProductId(
                    competitor.getCompetitorName(), product.getId());

            if (existingCompetitor.isPresent()) {
                Competitor existing = existingCompetitor.get();

                // Обновляем поля существующего конкурента
                updateCompetitorFields(existing, competitor);

                // Сохраняем обновленного конкурента
                Competitor savedCompetitor = competitorService.saveCompetitor(existing);

                log.debug("Обновлен существующий конкурент: {}", savedCompetitor.getCompetitorName());
                return savedCompetitor;
            }

            // Создаем нового конкурента
            Competitor savedCompetitor = competitorService.saveCompetitor(competitor);

            log.debug("Создан новый конкурент: {}", savedCompetitor.getId());
            return savedCompetitor;

        } catch (Exception e) {
            log.error("Ошибка при обработке данных Competitor: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Обновление полей существующего конкурента
     *
     * @param target целевой объект для обновления
     * @param source источник данных
     */
    private void updateCompetitorFields(Competitor target, Competitor source) {
        target.setCompetitorPrice(source.getCompetitorPrice());
        target.setCompetitorPromotionalPrice(source.getCompetitorPromotionalPrice());
        target.setCompetitorTime(source.getCompetitorTime());
        target.setCompetitorDate(source.getCompetitorDate());
        target.setCompetitorLocalDateTime(source.getCompetitorLocalDateTime());
        target.setCompetitorStockStatus(source.getCompetitorStockStatus());
        target.setCompetitorAdditionalPrice(source.getCompetitorAdditionalPrice());
        target.setCompetitorCommentary(source.getCompetitorCommentary());
        target.setCompetitorProductName(source.getCompetitorProductName());
        target.setCompetitorAdditional(source.getCompetitorAdditional());
        target.setCompetitorAdditional2(source.getCompetitorAdditional2());
        target.setCompetitorUrl(source.getCompetitorUrl());
        target.setCompetitorWebCacheUrl(source.getCompetitorWebCacheUrl());
    }

    /**
     * Экспорт данных из составных сущностей
     *
     * @param mainEntityType тип основной сущности
     * @param id идентификатор сущности
     * @param fields список полей для экспорта
     * @return карта данных для экспорта
     */
    public Map<String, String> exportEntityData(String mainEntityType, Long id, List<String> fields) {
        if (!"product".equals(mainEntityType)) {
            log.error("Экспорт поддерживается только для основной сущности 'product'");
            return Collections.emptyMap();
        }

        // Получаем продукт и связанные сущности
        Optional<Product> productOpt = productService.findById(id);
        if (productOpt.isEmpty()) {
            log.error("Продукт с ID {} не найден", id);
            return Collections.emptyMap();
        }

        Product product = productOpt.get();
        Map<String, String> result = new HashMap<>();

        // Обрабатываем каждое поле
        for (String field : fields) {
            String[] parts = field.split("\\.", 2);
            if (parts.length != 2) {
                log.warn("Некорректный формат поля: {}", field);
                continue;
            }

            String entityType = parts[0];
            String fieldName = parts[1];

            // В зависимости от типа сущности, получаем значение поля
            switch (entityType) {
                case "product":
                    addProductField(result, product, field, fieldName);
                    break;
                case "region":
                    // Для региона берем первый связанный регион (если есть)
                    if (!product.getRegionList().isEmpty()) {
                        Region region = product.getRegionList().get(0);
                        addRegionField(result, region, field, fieldName);
                    }
                    break;
                case "competitor":
                    // Для конкурента берем первого связанного конкурента (если есть)
                    if (!product.getCompetitorList().isEmpty()) {
                        Competitor competitor = product.getCompetitorList().get(0);
                        addCompetitorField(result, competitor, field, fieldName);
                    }
                    break;
                default:
                    log.warn("Неизвестный тип сущности: {}", entityType);
            }
        }

        return result;
    }

    /**
     * Добавление поля продукта в результат экспорта
     */
    private void addProductField(Map<String, String> result, Product product, String fullField, String fieldName) {
        try {
            // Используем рефлексию для получения значения поля
            java.lang.reflect.Method getter = product.getClass().getMethod(
                    "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
            Object value = getter.invoke(product);

            // Преобразуем значение в строку
            result.put(fullField, value != null ? transformerFactory.toString(value, null) : "");
        } catch (Exception e) {
            log.warn("Ошибка при получении поля {} продукта: {}", fieldName, e.getMessage());
        }
    }

    /**
     * Добавление поля региона в результат экспорта
     */
    private void addRegionField(Map<String, String> result, Region region, String fullField, String fieldName) {
        try {
            // Используем рефлексию для получения значения поля
            java.lang.reflect.Method getter = region.getClass().getMethod(
                    "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
            Object value = getter.invoke(region);

            // Преобразуем значение в строку
            result.put(fullField, value != null ? transformerFactory.toString(value, null) : "");
        } catch (Exception e) {
            log.warn("Ошибка при получении поля {} региона: {}", fieldName, e.getMessage());
        }
    }

    /**
     * Добавление поля конкурента в результат экспорта
     */
    private void addCompetitorField(Map<String, String> result, Competitor competitor, String fullField, String fieldName) {
        try {
            // Используем рефлексию для получения значения поля
            java.lang.reflect.Method getter = competitor.getClass().getMethod(
                    "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
            Object value = getter.invoke(competitor);

            // Преобразуем значение в строку
            result.put(fullField, value != null ? transformerFactory.toString(value, null) : "");
        } catch (Exception e) {
            log.warn("Ошибка при получении поля {} конкурента: {}", fieldName, e.getMessage());
        }
    }

    /**
     * Создание новой сущности по типу
     *
     * @param entityType тип сущности
     * @return новая сущность или null, если тип не поддерживается
     */
    public ImportableEntity createEntityInstance(String entityType) {
        ImportableEntity entity = null;

        switch (entityType) {
            case "product":
                entity = new Product();
                break;
            case "region":
                entity = new Region();
                break;
            case "competitor":
                entity = new Competitor();
                break;
            default:
                log.warn("Неизвестный тип сущности: {}", entityType);
                return null;
        }

        // Устанавливаем трансформер
        try {
            entity.getClass().getMethod("setTransformerFactory", ValueTransformerFactory.class)
                    .invoke(entity, transformerFactory);
        } catch (Exception e) {
            log.warn("Не удалось установить трансформер для сущности типа {}", entityType);
        }

        return entity;
    }

    /**
     * Очистка кэша продуктов
     */
    public void clearCache() {
        productCache.clear();
        log.debug("Кэш продуктов очищен");
    }
}