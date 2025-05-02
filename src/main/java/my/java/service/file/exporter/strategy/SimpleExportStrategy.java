// src/main/java/my/java/service/file/exporter/strategy/SimpleExportStrategy.java
package my.java.service.file.exporter.strategy;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class SimpleExportStrategy implements ExportProcessingStrategy {
    @Override
    public String getStrategyId() {
        return "simple";
    }

    @Override
    public String getDisplayName() {
        return "Прямой экспорт";
    }

    @Override
    public String getDescription() {
        return "Экспорт данных без изменений";
    }

    @Override
    public List<Map<String, String>> processData(
            List<Map<String, String>> data,
            List<String> fields,
            Map<String, String> params) {
        // Возвращаем данные без изменений
        return data;
    }
}