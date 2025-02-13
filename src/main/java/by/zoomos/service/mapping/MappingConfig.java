package by.zoomos.service.mapping;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация маппинга колонок файла на поля сущностей
 */
@Component
@ConfigurationProperties(prefix = "mapping")
@Data
@NoArgsConstructor
public class MappingConfig {

    /**
     * Маппинг для продуктов
     */
    private Map<String, String> product = new HashMap<>();

    /**
     * Маппинг для региональных данных
     */
    private Map<String, String> region = new HashMap<>();

    /**
     * Маппинг для данных конкурентов
     */
    private Map<String, String> competitor = new HashMap<>();

    /**
     * Настройки валидации
     */
    private ValidationConfig validation = new ValidationConfig();

    @Data
    @NoArgsConstructor
    public static class ValidationConfig {
        private boolean validateRequired = true;
        private boolean validateNumeric = true;
        private boolean validateDates = true;
        private Map<String, String> customValidators = new HashMap<>();
    }
}
