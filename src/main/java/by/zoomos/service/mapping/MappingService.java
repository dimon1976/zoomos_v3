package by.zoomos.service.mapping;

import by.zoomos.exception.MappingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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

    private final MappingConfig mappingConfig;

    /**
     * Получает индексы колонок для маппинга продукта
     *
     * @param headers заголовки файла
     * @return карта индексов колонок
     */
    public Map<String, Integer> getProductColumnIndexes(String[] headers) {
        log.debug("Получение индексов колонок для продукта");
        return getColumnIndexes(headers, mappingConfig.getProduct());
    }

    /**
     * Получает индексы колонок для маппинга региональных данных
     *
     * @param headers заголовки файла
     * @return карта индексов колонок
     */
    public Map<String, Integer> getRegionColumnIndexes(String[] headers) {
        log.debug("Получение индексов колонок для региональных данных");
        return getColumnIndexes(headers, mappingConfig.getRegion());
    }

    /**
     * Получает индексы колонок для маппинга данных конкурентов
     *
     * @param headers заголовки файла
     * @return карта индексов колонок
     */
    public Map<String, Integer> getCompetitorColumnIndexes(String[] headers) {
        log.debug("Получение индексов колонок для данных конкурентов");
        return getColumnIndexes(headers, mappingConfig.getCompetitor());
    }

    /**
     * Проверяет наличие обязательных колонок
     *
     * @param headers заголовки файла
     * @throws MappingException если отсутствуют обязательные колонки
     */
    public void validateRequiredColumns(String[] headers) {
        log.debug("Проверка обязательных колонок");
        if (!mappingConfig.getValidation().isValidateRequired()) {
            return;
        }

        Set<String> headerSet = Set.of(headers);

        Set<String> missingColumns = mappingConfig.getProduct().values().stream()
                .filter(column -> !headerSet.contains(column))
                .collect(Collectors.toSet());

        if (!missingColumns.isEmpty()) {
            throw new MappingException("Отсутствуют обязательные колонки: " +
                    String.join(", ", missingColumns));
        }
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
}
