package by.zoomos.service.mapping;

import by.zoomos.exception.MappingException;
import by.zoomos.model.entity.MappingConfig;
import by.zoomos.repository.MappingConfigRepository;
import by.zoomos.repository.ClientRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для работы с маппингом данных
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MappingService {

    private final DefaultMappingConfig defaultConfig;
    private final MappingConfigRepository mappingConfigRepository;
    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    /**
     * Получает индексы колонок для маппинга продукта
     */
    public Map<String, Integer> getProductColumnIndexes(String[] headers, Long clientId) {
        Map<String, String> mapping = getEffectiveMapping(clientId).getProduct();
        return getColumnIndexes(headers, mapping);
    }

    /**
     * Получает индексы колонок для маппинга региональных данных
     */
    public Map<String, Integer> getRegionColumnIndexes(String[] headers, Long clientId) {
        Map<String, String> mapping = getEffectiveMapping(clientId).getRegion();
        return getColumnIndexes(headers, mapping);
    }

    /**
     * Получает индексы колонок для маппинга данных конкурентов
     */
    public Map<String, Integer> getCompetitorColumnIndexes(String[] headers, Long clientId) {
        Map<String, String> mapping = getEffectiveMapping(clientId).getCompetitor();
        return getColumnIndexes(headers, mapping);
    }

    /**
     * Проверяет наличие обязательных колонок
     */
    public void validateRequiredColumns(String[] headers, Long clientId) {
        DefaultMappingConfig effective = getEffectiveMapping(clientId);
        if (!effective.getValidation().isValidateRequired()) {
            return;
        }

        Set<String> headerSet = Set.of(headers);
        Set<String> missingColumns = effective.getProduct().values().stream()
                .filter(column -> !headerSet.contains(column))
                .collect(Collectors.toSet());

        if (!missingColumns.isEmpty()) {
            throw new MappingException("Отсутствуют обязательные колонки: " + String.join(", ", missingColumns));
        }
    }


    /**
     * Получает список маппингов клиента
     */
    @Transactional(readOnly = true)
    public List<MappingConfig> getClientMappings(Long clientId) {
        return mappingConfigRepository.findByClientIdAndActiveTrue(clientId);
    }

    private DefaultMappingConfig getEffectiveMapping(Long clientId) {
        // Здесь можно добавить логику для получения пользовательского маппинга
        // и объединения его с дефолтным
        return defaultConfig;
    }

    private Map<String, Integer> getColumnIndexes(String[] headers, Map<String, String> mapping) {
        Map<String, Integer> indexes = new HashMap<>();
        Map<String, String> normalizedHeaders = normalizeHeaders(headers);

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue().toLowerCase();

            Integer index = Integer.valueOf(normalizedHeaders.get(columnName));
            if (index != null) {
                indexes.put(fieldName, index);
            } else {
                log.warn("Колонка '{}' не найдена в заголовках файла", columnName);
            }
        }

        return indexes;
    }

    private Map<String, String> normalizeHeaders(String[] headers) {
        Map<String, String> normalized = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            normalized.put(header, String.valueOf(i));
        }
        return normalized;
    }

    private void validateClient(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new IllegalArgumentException("Клиент не найден: " + clientId);
        }
    }


}