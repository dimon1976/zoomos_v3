// src/main/java/my/java/service/file/exporter/strategy/FilteredExportStrategy.java
package my.java.service.file.exporter.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Component
@Slf4j
public class FilteredExportStrategy implements ExportProcessingStrategy {

    @Override
    public String getStrategyId() {
        return "filtered";
    }

    @Override
    public String getDisplayName() {
        return "Фильтрованный экспорт";
    }

    @Override
    public String getDescription() {
        return "Экспорт с фильтрацией данных по заданным критериям";
    }

    @Override
    public List<Map<String, String>> processData(
            List<Map<String, String>> data,
            List<String> fields,
            Map<String, String> params) {

        if (data.isEmpty() || fields.isEmpty()) {
            return data;
        }

        // Создаем комбинированный предикат для фильтрации
        Predicate<Map<String, String>> filter = createFilter(params);

        // Применяем фильтр
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> row : data) {
            if (filter.test(row)) {
                result.add(row);
            }
        }

        log.info("Отфильтровано записей: {} из {}", result.size(), data.size());
        return result;
    }

    /**
     * Создает предикат для фильтрации данных на основе параметров
     */
    private Predicate<Map<String, String>> createFilter(Map<String, String> params) {
        Predicate<Map<String, String>> filter = row -> true; // Изначально пропускаем все

        // Фильтр по текстовому полю (поиск подстроки)
        if (params.containsKey("textField") && params.containsKey("textValue") &&
                !params.get("textValue").isEmpty()) {
            String field = params.get("textField");
            String value = params.get("textValue").toLowerCase();
            filter = filter.and(row -> {
                String fieldValue = row.getOrDefault(field, "").toLowerCase();
                return fieldValue.contains(value);
            });
        }

        // Фильтр по числовому диапазону
        if (params.containsKey("numericField")) {
            String field = params.get("numericField");

            // Минимальное значение
            if (params.containsKey("minValue") && !params.get("minValue").isEmpty()) {
                try {
                    double minValue = Double.parseDouble(params.get("minValue"));
                    filter = filter.and(row -> {
                        String fieldValue = row.getOrDefault(field, "");
                        if (fieldValue.isEmpty()) return false;
                        try {
                            double value = Double.parseDouble(fieldValue);
                            return value >= minValue;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    });
                } catch (NumberFormatException e) {
                    log.warn("Неверный формат минимального значения: {}", params.get("minValue"));
                }
            }

            // Максимальное значение
            if (params.containsKey("maxValue") && !params.get("maxValue").isEmpty()) {
                try {
                    double maxValue = Double.parseDouble(params.get("maxValue"));
                    filter = filter.and(row -> {
                        String fieldValue = row.getOrDefault(field, "");
                        if (fieldValue.isEmpty()) return false;
                        try {
                            double value = Double.parseDouble(fieldValue);
                            return value <= maxValue;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    });
                } catch (NumberFormatException e) {
                    log.warn("Неверный формат максимального значения: {}", params.get("maxValue"));
                }
            }
        }

        // Фильтр по диапазону дат
        if (params.containsKey("dateField")) {
            String field = params.get("dateField");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // Начальная дата
            if (params.containsKey("fromDate") && !params.get("fromDate").isEmpty()) {
                try {
                    LocalDate fromDate = LocalDate.parse(params.get("fromDate"), formatter);
                    filter = filter.and(row -> {
                        String fieldValue = row.getOrDefault(field, "");
                        if (fieldValue.isEmpty()) return false;
                        try {
                            LocalDate date = LocalDate.parse(fieldValue, formatter);
                            return !date.isBefore(fromDate);
                        } catch (Exception e) {
                            return false;
                        }
                    });
                } catch (Exception e) {
                    log.warn("Неверный формат начальной даты: {}", params.get("fromDate"));
                }
            }

            // Конечная дата
            if (params.containsKey("toDate") && !params.get("toDate").isEmpty()) {
                try {
                    LocalDate toDate = LocalDate.parse(params.get("toDate"), formatter);
                    filter = filter.and(row -> {
                        String fieldValue = row.getOrDefault(field, "");
                        if (fieldValue.isEmpty()) return false;
                        try {
                            LocalDate date = LocalDate.parse(fieldValue, formatter);
                            return !date.isAfter(toDate);
                        } catch (Exception e) {
                            return false;
                        }
                    });
                } catch (Exception e) {
                    log.warn("Неверный формат конечной даты: {}", params.get("toDate"));
                }
            }
        }

        return filter;
    }
}