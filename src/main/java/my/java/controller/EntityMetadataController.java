// src/main/java/my/java/controller/EntityMetadataController.java
package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.service.file.metadata.EntityFieldService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Контроллер для предоставления метаданных о полях сущностей.
 * Предоставляет API для получения информации о структуре сущностей.
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@Slf4j
public class EntityMetadataController {

    private final EntityFieldService entityFieldService;

    /**
     * Получает список полей для указанного типа сущности.
     * Используется страницей экспорта для построения списка доступных полей.
     *
     * @param entityType тип сущности (product, region, competitor, product_with_related)
     * @return список метаданных полей для фронтенда
     */
    @GetMapping("/fields/{entityType}")
    public ResponseEntity<List<Map<String, String>>> getEntityFields(@PathVariable String entityType) {
        log.debug("GET запрос на получение полей для типа сущности: {}", entityType);

        // Маппинг типов сущностей с фронтенда в ключи EntityFieldService
        String serviceEntityType = mapEntityType(entityType);

        // Получаем доступные поля из сервиса
        Map<String, String> fieldMappings = entityFieldService.getFieldMappings(serviceEntityType);

        // Проверка на пустой результат
        if (fieldMappings.isEmpty()) {
            log.warn("Поля для типа {} (сервисный тип: {}) не найдены", entityType, serviceEntityType);
        } else {
            log.debug("Найдено {} полей для типа {} (сервисный тип: {})",
                    fieldMappings.size(), entityType, serviceEntityType);
        }

        // Преобразуем в формат, ожидаемый фронтендом
        List<Map<String, String>> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            Map<String, String> field = new HashMap<>();
            field.put("name", entry.getKey());
            field.put("displayName", entry.getValue());
            result.add(field);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Получает список всех поддерживаемых типов сущностей в формате для фронтенда.
     *
     * @return список типов сущностей
     */
    @GetMapping("/entity-types")
    public ResponseEntity<List<String>> getSupportedEntityTypes() {
        log.debug("GET запрос на получение поддерживаемых типов сущностей");

        List<String> serviceEntityTypes = entityFieldService.getSupportedEntityTypes();
        List<String> frontendEntityTypes = new ArrayList<>();

        // Преобразуем типы сущностей из сервисного формата в формат для фронтенда
        for (String serviceType : serviceEntityTypes) {
            String frontendType = mapServiceTypeToFrontend(serviceType);
            frontendEntityTypes.add(frontendType);
        }

        log.debug("Возвращаем {} типов сущностей для фронтенда", frontendEntityTypes.size());
        return ResponseEntity.ok(frontendEntityTypes);
    }

    /**
     * Получает полную информацию о структуре полей, включая группировку, для указанного типа сущности.
     *
     * @param entityType тип сущности
     * @return полная структура полей или пустой объект
     */
    @GetMapping("/structure/{entityType}")
    public ResponseEntity<Map<String, Object>> getEntityStructure(@PathVariable String entityType) {
        log.debug("API запрос на получение структуры полей для сущности: {}", entityType);

        try {
            // Маппинг типа сущности в правильный формат
            String serviceEntityType = mapEntityType(entityType);
            log.debug("Преобразован тип сущности из {} в {}", entityType, serviceEntityType);

            Map<String, Object> structure = new HashMap<>();

            // Получаем обычные поля
            Map<String, String> fieldMappings = entityFieldService.getFieldMappings(serviceEntityType);
            structure.put("fields", fieldMappings);

            // Если это составной тип, получаем группы полей
            if (serviceEntityType.contains("_with_")) {
                Map<String, List<Map.Entry<String, String>>> fieldGroups =
                        entityFieldService.getFieldGroups(serviceEntityType);
                structure.put("groups", fieldGroups);
            }

            return ResponseEntity.ok(structure);
        } catch (Exception e) {
            log.error("Ошибка при получении структуры полей: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Collections.emptyMap());
        }
    }

    /**
     * Преобразует тип сущности из формата фронтенда в формат, ожидаемый EntityFieldService.
     *
     * @param entityType тип сущности с фронтенда
     * @return соответствующий тип сущности для сервиса
     */
    private String mapEntityType(String entityType) {
        if (entityType == null) {
            return "";
        }

        String type = entityType.toLowerCase();

        // Маппинг типов
        switch (type) {
            case "competitor":
                return "competitordata";
            case "region":
                return "regiondata";
            case "product":
            case "product_with_related":
                return type; // Эти значения совпадают
            default:
                // Возвращаем оригинальное значение, если не найдено соответствие
                log.warn("Неизвестный тип сущности: {}", type);
                return type;
        }
    }

    /**
     * Преобразует тип сущности из формата сервиса в формат для фронтенда.
     *
     * @param serviceType тип сущности из сервиса
     * @return тип сущности для фронтенда
     */
    private String mapServiceTypeToFrontend(String serviceType) {
        if (serviceType == null) {
            return "";
        }

        String type = serviceType.toLowerCase();

        // Обратный маппинг типов
        switch (type) {
            case "competitordata":
                return "competitor";
            case "regiondata":
                return "region";
            default:
                // Возвращаем оригинальное значение для остальных типов
                return type;
        }
    }
}