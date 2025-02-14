package by.zoomos.controller;

import by.zoomos.service.mapping.DefaultMappingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Контроллер для управления маппингом данных
 */
@RestController
@RequestMapping("/api/v1/mapping")
@RequiredArgsConstructor
@Slf4j
public class MappingController {

    private final DefaultMappingConfig defaultMappingConfig;

    /**
     * Получает текущую конфигурацию маппинга
     *
     * @return конфигурация маппинга
     */
    @GetMapping
    public ResponseEntity<DefaultMappingConfig> getDefaultMappingConfig() {
        log.debug("Получение конфигурации маппинга");
        return ResponseEntity.ok(defaultMappingConfig);
    }

    /**
     * Обновляет маппинг для продуктов
     *
     * @param mapping новый маппинг
     * @return обновленный маппинг
     */
    @PutMapping("/product")
    public ResponseEntity<Map<String, String>> updateProductMapping(
            @RequestBody Map<String, String> mapping) {
        log.info("Обновление маппинга продуктов: {}", mapping);
        defaultMappingConfig.setProduct(mapping);
        return ResponseEntity.ok(mapping);
    }

    /**
     * Обновляет маппинг для региональных данных
     *
     * @param mapping новый маппинг
     * @return обновленный маппинг
     */
    @PutMapping("/region")
    public ResponseEntity<Map<String, String>> updateRegionMapping(
            @RequestBody Map<String, String> mapping) {
        log.info("Обновление маппинга региональных данных: {}", mapping);
        defaultMappingConfig.setRegion(mapping);
        return ResponseEntity.ok(mapping);
    }

    /**
     * Обновляет маппинг для данных конкурентов
     *
     * @param mapping новый маппинг
     * @return обновленный маппинг
     */
    @PutMapping("/competitor")
    public ResponseEntity<Map<String, String>> updateCompetitorMapping(
            @RequestBody Map<String, String> mapping) {
        log.info("Обновление маппинга данных конкурентов: {}", mapping);
        defaultMappingConfig.setCompetitor(mapping);
        return ResponseEntity.ok(mapping);
    }

    /**
     * Обновляет настройки валидации
     *
     * @param config новые настройки
     * @return обновленные настройки
     */
    @PutMapping("/validation")
    public ResponseEntity<DefaultMappingConfig.ValidationConfig> updateValidationConfig(
            @RequestBody DefaultMappingConfig.ValidationConfig config) {
        log.info("Обновление настроек валидации: {}", config);
        defaultMappingConfig.setValidation(config);
        return ResponseEntity.ok(config);
    }
}
