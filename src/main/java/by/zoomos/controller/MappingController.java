package by.zoomos.controller;

import by.zoomos.service.mapping.MappingConfig;
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

    private final MappingConfig mappingConfig;

    /**
     * Получает текущую конфигурацию маппинга
     *
     * @return конфигурация маппинга
     */
    @GetMapping
    public ResponseEntity<MappingConfig> getMappingConfig() {
        log.debug("Получение конфигурации маппинга");
        return ResponseEntity.ok(mappingConfig);
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
        mappingConfig.setProduct(mapping);
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
        mappingConfig.setRegion(mapping);
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
        mappingConfig.setCompetitor(mapping);
        return ResponseEntity.ok(mapping);
    }

    /**
     * Обновляет настройки валидации
     *
     * @param config новые настройки
     * @return обновленные настройки
     */
    @PutMapping("/validation")
    public ResponseEntity<MappingConfig.ValidationConfig> updateValidationConfig(
            @RequestBody MappingConfig.ValidationConfig config) {
        log.info("Обновление настроек валидации: {}", config);
        mappingConfig.setValidation(config);
        return ResponseEntity.ok(config);
    }
}
