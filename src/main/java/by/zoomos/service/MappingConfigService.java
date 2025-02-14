package by.zoomos.service;

import by.zoomos.model.entity.MappingConfig;
import by.zoomos.model.dto.MappingConfigDTO;
import by.zoomos.repository.MappingConfigRepository;
import by.zoomos.repository.ClientRepository;
import by.zoomos.service.mapping.DefaultMappingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с конфигурациями маппинга
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MappingConfigService {

    private final MappingConfigRepository mappingConfigRepository;
    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper;
    private final DefaultMappingConfig defaultConfig;

    /**
     * Получает или создает конфигурацию маппинга для клиента
     */
    @Transactional
    public MappingConfig getOrCreateMapping(Long clientId, MappingConfig.FileType fileType) {
        return mappingConfigRepository.findByClientIdAndFileTypeAndActiveTrue(clientId, fileType)
                .stream()
                .filter(MappingConfig::isDefault)
                .findFirst()
                .orElseGet(() -> createDefaultMapping(clientId, fileType));
    }

    /**
     * Создает конфигурацию маппинга по умолчанию
     */
    private MappingConfig createDefaultMapping(Long clientId, MappingConfig.FileType fileType) {
        MappingConfig mapping = new MappingConfig();
        mapping.setClientId(clientId);
        mapping.setName("Default " + fileType + " Mapping");
        mapping.setFileType(fileType);
        mapping.setDefault(true);
        mapping.setActive(true);

        // Копируем настройки из дефолтной конфигурации
        mapping.setProductMapping(new HashMap<>(defaultConfig.getProduct()));
        mapping.setRegionMapping(new HashMap<>(defaultConfig.getRegion()));
        mapping.setCompetitorMapping(new HashMap<>(defaultConfig.getCompetitor()));

        return mappingConfigRepository.save(mapping);
    }

    /**
     * Создает новую конфигурацию маппинга
     */
    @Transactional
    public MappingConfig createMapping(MappingConfigDTO dto) {
        log.info("Создание новой конфигурации маппинга для клиента: {}", dto.getClientId());

        validateClient(dto.getClientId());

        // Проверяем, нет ли уже активной конфигурации по умолчанию
        if (dto.isDefault()) {
            mappingConfigRepository.findByClientIdAndIsDefaultTrue(dto.getClientId())
                    .ifPresent(existingDefault -> {
                        existingDefault.setDefault(false);
                        mappingConfigRepository.save(existingDefault);
                    });
        }

        MappingConfig config = new MappingConfig();
        updateMappingFromDto(config, dto);
        return mappingConfigRepository.save(config);
    }

    /**
     * Обновляет существующую конфигурацию
     */
    @Transactional
    public MappingConfig updateMapping(Long id, MappingConfigDTO dto) {
        log.info("Обновление конфигурации маппинга: {}", id);

        MappingConfig config = mappingConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Конфигурация не найдена: " + id));

        // Если меняется клиент, проверяем его существование
        if (!config.getClientId().equals(dto.getClientId())) {
            validateClient(dto.getClientId());
        }

        // Если устанавливается флаг по умолчанию
        if (dto.isDefault() && !config.isDefault()) {
            mappingConfigRepository.clearDefaultForClient(dto.getClientId());
        }

        updateMappingFromDto(config, dto);
        return mappingConfigRepository.save(config);
    }

    /**
     * Получает конфигурацию по ID
     */
    @Transactional(readOnly = true)
    public MappingConfig getMapping(Long id) {
        return mappingConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Конфигурация не найдена: " + id));
    }

    /**
     * Получает список конфигураций для клиента
     */
    @Transactional(readOnly = true)
    public List<MappingConfig> getMappingsForClient(Long clientId) {
        return mappingConfigRepository.findByClientIdAndActiveTrue(clientId);
    }

    /**
     * Получает конфигурацию по умолчанию для клиента
     */
    @Transactional(readOnly = true)
    public MappingConfig getDefaultMapping(Long clientId) {
        return mappingConfigRepository.findByClientIdAndIsDefaultTrue(clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Конфигурация по умолчанию не найдена для клиента: " + clientId));
    }

    /**
     * Деактивирует конфигурацию
     */
    @Transactional
    public void deactivateMapping(Long id) {
        log.info("Деактивация конфигурации маппинга: {}", id);

        MappingConfig config = mappingConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Конфигурация не найдена: " + id));

        // Если это конфигурация по умолчанию, сначала снимаем этот флаг
        if (config.isDefault()) {
            config.setDefault(false);
        }

        config.setActive(false);
        mappingConfigRepository.save(config);
    }

    /**
     * Устанавливает конфигурацию по умолчанию
     */
    @Transactional
    public void setDefaultMapping(Long id) {
        log.info("Установка конфигурации по умолчанию: {}", id);

        MappingConfig config = mappingConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Конфигурация не найдена: " + id));

        mappingConfigRepository.clearDefaultForClient(config.getClientId());
        config.setDefault(true);
        mappingConfigRepository.save(config);
    }

    private void validateClient(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new IllegalArgumentException("Клиент не найден: " + clientId);
        }
    }

    private void updateMappingFromDto(MappingConfig config, MappingConfigDTO dto) {
        config.setClientId(dto.getClientId());
        config.setName(dto.getName());
        config.setDescription(dto.getDescription());
        config.setFileType(MappingConfig.FileType.valueOf(dto.getFileType()));
        config.setDefault(dto.isDefault());
        config.setActive(dto.isActive());

        // Простое копирование мап, так как типы совпадают
        config.setProductMapping(dto.getProductMapping());
        config.setRegionMapping(dto.getRegionMapping());
        config.setCompetitorMapping(dto.getCompetitorMapping());
    }

    /**
     * Конвертирует конфигурацию в DTO
     */
    public MappingConfigDTO convertToDto(MappingConfig config) {
        MappingConfigDTO dto = new MappingConfigDTO();
        dto.setId(config.getId());
        dto.setClientId(config.getClientId());
        dto.setName(config.getName());
        dto.setDescription(config.getDescription());
        dto.setFileType(config.getFileType().name());
        dto.setDefault(config.isDefault());
        dto.setActive(config.isActive());

        // Простое копирование мап, так как типы совпадают
        dto.setProductMapping(config.getProductMapping());
        dto.setRegionMapping(config.getRegionMapping());
        dto.setCompetitorMapping(config.getCompetitorMapping());

        return dto;
    }
}