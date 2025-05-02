// src/main/java/my/java/service/file/exporter/strategy/ExportProcessingStrategy.java
package my.java.service.file.exporter.strategy;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для стратегий обработки данных перед экспортом
 */
public interface ExportProcessingStrategy {
    /**
     * Возвращает идентификатор стратегии
     */
    String getStrategyId();

    /**
     * Возвращает отображаемое имя стратегии
     */
    String getDisplayName();

    /**
     * Возвращает описание стратегии
     */
    String getDescription();

    /**
     * Обрабатывает данные перед экспортом
     */
    List<Map<String, String>> processData(
            List<Map<String, String>> data,
            List<String> fields,
            Map<String, String> params
    );
}