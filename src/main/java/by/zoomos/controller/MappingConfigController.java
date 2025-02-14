package by.zoomos.controller;

import by.zoomos.model.dto.MappingConfigDTO;
import by.zoomos.model.entity.MappingConfig;
import by.zoomos.service.MappingConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST контроллер для работы с конфигурациями маппинга
 */
@RestController
@RequestMapping("/api/v1/mappings")
@RequiredArgsConstructor
@Slf4j
public class MappingConfigController {

    private final MappingConfigService mappingConfigService;

    /**
     * Создает новую конфигурацию
     */
    @PostMapping
    public ResponseEntity<MappingConfigDTO> createMapping(@RequestBody MappingConfigDTO dto) {
        log.info("Создание новой конфигурации маппинга: {}", dto); // Добавим подробное логирование
        try {
            MappingConfig config = mappingConfigService.createMapping(dto);
            MappingConfigDTO result = mappingConfigService.convertToDto(config);
            log.info("Успешно создана конфигурация маппинга: {}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка при создании конфигурации маппинга", e);
            throw e;
        }
    }

    /**
     * Обновляет существующую конфигурацию
     */
    @PutMapping("/{id}")
    public ResponseEntity<MappingConfigDTO> updateMapping(
            @PathVariable Long id,
            @RequestBody MappingConfigDTO dto) {
        log.info("Обновление конфигурации маппинга: {}", id);
        MappingConfig config = mappingConfigService.updateMapping(id, dto);
        return ResponseEntity.ok(mappingConfigService.convertToDto(config));
    }

    /**
     * Получает конфигурацию по ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<MappingConfigDTO> getMapping(@PathVariable Long id) {
        MappingConfig config = mappingConfigService.getMapping(id);
        return ResponseEntity.ok(mappingConfigService.convertToDto(config));
    }

    /**
     * Получает список конфигураций для клиента
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<MappingConfigDTO>> getMappingsForClient(@PathVariable Long clientId) {
        List<MappingConfig> configs = mappingConfigService.getMappingsForClient(clientId);
        List<MappingConfigDTO> dtos = configs.stream()
                .map(mappingConfigService::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Получает конфигурацию по умолчанию для клиента
     */
    @GetMapping("/client/{clientId}/default")
    public ResponseEntity<MappingConfigDTO> getDefaultMapping(@PathVariable Long clientId) {
        MappingConfig config = mappingConfigService.getDefaultMapping(clientId);
        return ResponseEntity.ok(mappingConfigService.convertToDto(config));
    }

    /**
     * Деактивирует конфигурацию
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateMapping(@PathVariable Long id) {
        log.info("Деактивация конфигурации маппинга: {}", id);
        mappingConfigService.deactivateMapping(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Устанавливает конфигурацию по умолчанию
     */
    @PostMapping("/{id}/default")
    public ResponseEntity<Void> setDefaultMapping(@PathVariable Long id) {
        log.info("Установка конфигурации по умолчанию: {}", id);
        mappingConfigService.setDefaultMapping(id);
        return ResponseEntity.ok().build();
    }
}