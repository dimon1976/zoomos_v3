package my.java.service.mapping;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FieldMappingDto;
import my.java.dto.FieldMappingDto.FieldMappingDetailDto;
import my.java.model.*;
import my.java.model.entity.*;
import my.java.repository.FieldMappingRepository;
import my.java.service.client.ClientService;
import my.java.util.transformer.ValueTransformerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для управления шаблонами маппинга полей
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FieldMappingService {

    private final FieldMappingRepository fieldMappingRepository;
    private final ClientService clientService;
    private final ValueTransformerFactory transformerFactory;

    /**
     * Получить все шаблоны для клиента
     */
    @Transactional(readOnly = true)
    public List<FieldMappingDto> getAllMappingsForClient(Long clientId) {
        log.debug("Getting all field mappings for client: {}", clientId);

        Client client = clientService.findClientEntityById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Клиент не найден"));

        List<FieldMapping> mappings = fieldMappingRepository.findByClientOrderByNameAsc(client);

        return mappings.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Получить активные шаблоны для клиента
     */
    @Transactional(readOnly = true)
    public List<FieldMappingDto> getActiveMappingsForClient(Long clientId, String entityType) {
        log.debug("Getting active field mappings for client: {} and entity type: {}", clientId, entityType);

        Client client = clientService.findClientEntityById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Клиент не найден"));

        List<FieldMapping> mappings;
        if (entityType != null && !entityType.isEmpty()) {
            mappings = fieldMappingRepository.findByClientAndEntityTypeAndIsActive(client, entityType, true);
        } else {
            mappings = fieldMappingRepository.findByClientAndIsActiveOrderByNameAsc(client, true);
        }

        return mappings.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Получить шаблон по ID
     */
    @Transactional(readOnly = true)
    public Optional<FieldMappingDto> getMappingById(Long id) {
        log.debug("Getting field mapping by id: {}", id);

        return fieldMappingRepository.findByIdWithDetails(id)
                .map(this::mapToDto);
    }

    /**
     * Создать новый шаблон
     */
    @Transactional
    public FieldMappingDto createMapping(FieldMappingDto dto) {
        log.debug("Creating new field mapping: {}", dto.getName());

        Client client = clientService.findClientEntityById(dto.getClientId())
                .orElseThrow(() -> new EntityNotFoundException("Клиент не найден"));

        // Проверка уникальности имени
        if (fieldMappingRepository.existsByClientAndNameIgnoreCase(client, dto.getName())) {
            throw new IllegalArgumentException("Шаблон с именем '" + dto.getName() + "' уже существует");
        }

        FieldMapping mapping = mapToEntity(dto);
        mapping.setClient(client);

        // Сохраняем детали
        if (dto.getDetails() != null) {
            dto.getDetails().forEach(detailDto -> {
                FieldMappingDetail detail = mapDetailToEntity(detailDto);
                mapping.addDetail(detail);
            });
        }

        FieldMapping savedMapping = fieldMappingRepository.save(mapping);
        log.info("Created field mapping with id: {}", savedMapping.getId());

        return mapToDto(savedMapping);
    }

    /**
     * Обновить существующий шаблон
     */
    @Transactional
    public FieldMappingDto updateMapping(Long id, FieldMappingDto dto) {
        log.debug("Updating field mapping with id: {}", id);
        log.debug("DTO details count: {}", dto.getDetails() != null ? dto.getDetails().size() : 0);

        FieldMapping mapping = fieldMappingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Шаблон с ID " + id + " не найден"));

        log.debug("Current mapping details count: {}", mapping.getDetails().size());

        // Проверка уникальности имени при изменении
        if (!mapping.getName().equalsIgnoreCase(dto.getName()) &&
                fieldMappingRepository.existsByClientAndNameIgnoreCase(mapping.getClient(), dto.getName())) {
            throw new IllegalArgumentException("Шаблон с именем '" + dto.getName() + "' уже существует");
        }

        // Обновляем поля
        updateEntityFromDto(mapping, dto);

        // Обновляем детали
        log.debug("Clearing existing details");
        mapping.getDetails().clear();

        if (dto.getDetails() != null) {
            log.debug("Adding {} new details", dto.getDetails().size());
            dto.getDetails().forEach(detailDto -> {
                FieldMappingDetail detail = mapDetailToEntity(detailDto);
                mapping.addDetail(detail);
                log.debug("Added detail: source={}, target={}, entity={}",
                        detail.getSourceField(), detail.getTargetField(), detail.getTargetEntity());
            });
        } else {
            log.warn("DTO details is null!");
        }

        log.debug("Saving mapping with {} details", mapping.getDetails().size());
        FieldMapping updatedMapping = fieldMappingRepository.save(mapping);
        log.info("Updated field mapping with id: {}, details count: {}", id, updatedMapping.getDetails().size());

        return mapToDto(updatedMapping);
    }

    /**
     * Удалить шаблон
     */
    @Transactional
    public boolean deleteMapping(Long id) {
        log.debug("Deleting field mapping with id: {}", id);

        if (!fieldMappingRepository.existsById(id)) {
            log.warn("Field mapping with id: {} not found for deletion", id);
            return false;
        }

        fieldMappingRepository.deleteById(id);
        log.info("Deleted field mapping with id: {}", id);
        return true;
    }

    /**
     * Получить доступные поля для маппинга по типу сущности
     */
    public Map<String, Map<String, String>> getAvailableFieldsForMapping(String entityType) {
        Map<String, Map<String, String>> result = new HashMap<>();

        if ("COMBINED".equals(entityType)) {
            // Для составного типа возвращаем поля всех сущностей
            result.put("PRODUCT", new Product().getFieldMappings());
            result.put("COMPETITOR", new Competitor().getFieldMappings());
            result.put("REGION", new Region().getFieldMappings());
            result.put("HANDBOOK", new AvHandbook().getFieldMappings());
        } else {
            // Для одиночного типа возвращаем поля конкретной сущности
            ImportableEntity entity = createEntityByType(entityType);
            if (entity != null) {
                result.put(entityType, entity.getFieldMappings());
            }
        }

        return result;
    }

    /**
     * Применить шаблон маппинга к данным из CSV
     */
    public Map<String, ImportableEntity> applyMapping(FieldMapping mapping, Map<String, String> csvRow) {
        log.debug("Applying field mapping '{}' to CSV row with {} fields", mapping.getName(), csvRow.size());
        log.debug("CSV row keys: {}", csvRow.keySet());

        // Показываем первые несколько значений для диагностики
        csvRow.entrySet().stream().limit(3).forEach(entry ->
                log.debug("CSV field '{}' = '{}'", entry.getKey(), entry.getValue())
        );

        Map<String, ImportableEntity> result = new HashMap<>();

        // Группируем детали по целевым сущностям
        Map<String, List<FieldMappingDetail>> detailsByEntity = mapping.getDetails().stream()
                .filter(d -> d.getTargetEntity() != null)
                .collect(Collectors.groupingBy(FieldMappingDetail::getTargetEntity));

        log.debug("Found mappings for {} entity types: {}", detailsByEntity.size(), detailsByEntity.keySet());

        // Обрабатываем каждую сущность
        for (Map.Entry<String, List<FieldMappingDetail>> entry : detailsByEntity.entrySet()) {
            String entityType = entry.getKey();
            List<FieldMappingDetail> details = entry.getValue();

            log.debug("Processing entity type '{}' with {} field mappings", entityType, details.size());

            ImportableEntity entity = createEntityByType(entityType);
            if (entity == null) {
                log.warn("Unknown entity type: {}", entityType);
                continue;
            }

            // Устанавливаем трансформер
            entity.setTransformerFactory(transformerFactory);

            // Создаем Map для заполнения сущности
            Map<String, String> entityData = new HashMap<>();

            for (FieldMappingDetail detail : details) {
                String sourceField = detail.getSourceField();
                String targetField = detail.getTargetField();
                String sourceValue = csvRow.get(sourceField);

                log.debug("Mapping: '{}' -> '{}', value: '{}'", sourceField, targetField, sourceValue);

                // Если значение пустое, используем значение по умолчанию
                if (sourceValue == null || sourceValue.trim().isEmpty()) {
                    sourceValue = detail.getDefaultValue();
                    if (sourceValue != null) {
                        log.trace("Using default value for '{}': '{}'", targetField, sourceValue);
                    }
                }

                // Применяем трансформацию если нужно
                if (sourceValue != null && detail.getTransformationType() != null) {
                    log.trace("Applying transformation '{}' to value '{}'", detail.getTransformationType(), sourceValue);
                    // TODO: Применить трансформацию через ValueTransformerFactory
                }

                entityData.put(targetField, sourceValue);
            }

            log.debug("Created entity data for '{}': {}", entityType, entityData);

            // Заполняем сущность данными
            boolean fillSuccess = entity.fillFromMap(entityData);

            if (fillSuccess) {
                // Сущность уже заполнена методом fillFromMap, добавляем её в результат
                result.put(entityType, entity);
                log.debug("Successfully filled entity '{}' with data", entityType);
            } else {
                log.warn("Failed to fill entity '{}' from data: {}", entityType, entityData);
            }
        }

        log.debug("Mapping result: created {} entities", result.size());
        return result;
    }

    /**
     * Создать сущность по типу
     */
    private ImportableEntity createEntityByType(String entityType) {
        switch (entityType) {
            case "PRODUCT":
                return new Product();
            case "COMPETITOR":
                return new Competitor();
            case "REGION":
                return new Region();
            default:
                return null;
        }
    }

    /**
     * Маппинг Entity -> DTO
     */
    private FieldMappingDto mapToDto(FieldMapping entity) {
        FieldMappingDto dto = FieldMappingDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .clientId(entity.getClient() != null ? entity.getClient().getId() : null)
                .clientName(entity.getClient() != null ? entity.getClient().getName() : null)
                .entityType(entity.getEntityType())
                .importType(entity.getImportType())
                .fileEncoding(entity.getFileEncoding())
                .csvDelimiter(entity.getCsvDelimiter())
                .csvQuoteChar(entity.getCsvQuoteChar())
                .duplicateStrategy(entity.getDuplicateStrategy())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .entityTypeDisplay(entity.getEntityTypeDisplay())
                .importTypeDisplay(entity.getImportTypeDisplay())
                .duplicateStrategyDisplay(entity.getDuplicateStrategyDisplay())
                .build();

        // Маппим детали
        if (entity.getDetails() != null) {
            dto.setDetails(entity.getDetails().stream()
                    .map(this::mapDetailToDto)
                    .collect(Collectors.toList()));
            dto.setDetailsCount(entity.getDetails().size());
        }

        return dto;
    }

    /**
     * Маппинг DTO -> Entity
     */
    private FieldMapping mapToEntity(FieldMappingDto dto) {
        return FieldMapping.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .entityType(dto.getEntityType())
                .importType(dto.getImportType())
                .fileEncoding(dto.getFileEncoding())
                .csvDelimiter(dto.getCsvDelimiter())
                .csvQuoteChar(dto.getCsvQuoteChar())
                .duplicateStrategy(dto.getDuplicateStrategy())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
    }

    /**
     * Обновление Entity из DTO
     */
    private void updateEntityFromDto(FieldMapping entity, FieldMappingDto dto) {
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setEntityType(dto.getEntityType());
        entity.setImportType(dto.getImportType());
        entity.setFileEncoding(dto.getFileEncoding());
        entity.setCsvDelimiter(dto.getCsvDelimiter());
        entity.setCsvQuoteChar(dto.getCsvQuoteChar());
        entity.setDuplicateStrategy(dto.getDuplicateStrategy());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
    }

    /**
     * Маппинг FieldMappingDetail Entity -> DTO
     */
    private FieldMappingDetailDto mapDetailToDto(FieldMappingDetail entity) {
        return FieldMappingDetailDto.builder()
                .id(entity.getId())
                .sourceField(entity.getSourceField())
                .targetField(entity.getTargetField())
                .targetEntity(entity.getTargetEntity())
                .required(entity.getRequired())
                .transformationType(entity.getTransformationType())
                .transformationParams(entity.getTransformationParams())
                .defaultValue(entity.getDefaultValue())
                .orderIndex(entity.getOrderIndex())
                .targetEntityDisplay(entity.getTargetEntityDisplay())
                .fullTargetFieldName(entity.getFullTargetFieldName())
                .build();
    }

    /**
     * Маппинг FieldMappingDetailDto -> Entity
     */
    private FieldMappingDetail mapDetailToEntity(FieldMappingDetailDto dto) {
        return FieldMappingDetail.builder()
                .sourceField(dto.getSourceField())
                .targetField(dto.getTargetField())
                .targetEntity(dto.getTargetEntity())
                .required(dto.getRequired() != null ? dto.getRequired() : false)
                .transformationType(dto.getTransformationType())
                .transformationParams(dto.getTransformationParams())
                .defaultValue(dto.getDefaultValue())
                .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : 0)
                .build();
    }
}