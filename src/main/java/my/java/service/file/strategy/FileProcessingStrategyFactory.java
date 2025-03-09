package my.java.service.file.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation.OperationType;
import my.java.service.file.strategy.import_strategy.ImportFileStrategy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Фабрика для создания стратегий обработки файлов.
 * Выбирает подходящую стратегию в зависимости от типа операции и параметров.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FileProcessingStrategyFactory {

    // Стратегии обработки файлов
    private final ImportFileStrategy importFileStrategy;

    // Другие стратегии, которые могут быть добавлены в будущем
    // private final ExportFileStrategy exportFileStrategy;
    // private final ProcessFileStrategy processFileStrategy;

    // Стратегии по умолчанию для каждого типа операции
    private final Map<OperationType, FileProcessingStrategy> defaultStrategies = new HashMap<>();

    /**
     * Инициализирует фабрику.
     */
    {
        // Инициализируем стратегии по умолчанию после внедрения зависимостей
    }

    /**
     * Инициализирует стратегии по умолчанию.
     * Этот метод вызывается после внедрения зависимостей.
     */
    private void initializeDefaultStrategies() {
        if (defaultStrategies.isEmpty()) {
            defaultStrategies.put(OperationType.IMPORT, importFileStrategy);
            // defaultStrategies.put(OperationType.EXPORT, exportFileStrategy);
            // defaultStrategies.put(OperationType.PROCESS, processFileStrategy);
        }
    }

    /**
     * Создает стратегию обработки файла в зависимости от типа операции и параметров.
     *
     * @param operationType тип операции
     * @param params параметры стратегии
     * @return стратегия обработки файла
     */
    public FileProcessingStrategy createStrategy(OperationType operationType, Map<String, Object> params) {
        // Инициализируем стратегии, если еще не инициализированы
        initializeDefaultStrategies();

        // По умолчанию используем стратегию для указанного типа операции
        FileProcessingStrategy strategy = defaultStrategies.get(operationType);

        // Если нет стратегии по умолчанию или указана другая стратегия в параметрах,
        // выбираем стратегию на основе параметров
        if (strategy == null || hasCustomStrategy(params)) {
            strategy = selectCustomStrategy(operationType, params);
        }

        if (strategy == null) {
            log.warn("Стратегия не найдена для типа операции: {}", operationType);
            throw new IllegalArgumentException("Неподдерживаемый тип операции: " + operationType);
        }

        return strategy;
    }

    /**
     * Проверяет, указана ли пользовательская стратегия в параметрах.
     *
     * @param params параметры стратегии
     * @return true, если указана пользовательская стратегия
     */
    private boolean hasCustomStrategy(Map<String, Object> params) {
        return params != null && params.containsKey("strategyName");
    }

    /**
     * Выбирает пользовательскую стратегию на основе параметров.
     *
     * @param operationType тип операции
     * @param params параметры стратегии
     * @return выбранная стратегия
     */
    private FileProcessingStrategy selectCustomStrategy(OperationType operationType, Map<String, Object> params) {
        if (params == null) {
            return defaultStrategies.get(operationType);
        }

        String strategyName = (String) params.get("strategyName");
        if (strategyName == null || strategyName.isEmpty()) {
            return defaultStrategies.get(operationType);
        }

        // Выбираем стратегию по имени
        switch (strategyName.toLowerCase()) {
            case "import":
            case "importfile":
                return importFileStrategy;
            // case "export":
            // case "exportfile":
            //     return exportFileStrategy;
            // case "process":
            // case "processfile":
            //     return processFileStrategy;
            default:
                log.warn("Неизвестное имя стратегии: {}", strategyName);
                return defaultStrategies.get(operationType);
        }
    }

    /**
     * Возвращает список доступных стратегий.
     *
     * @return список доступных стратегий
     */
    public List<FileProcessingStrategy> getAllStrategies() {
        // Инициализируем стратегии, если еще не инициализированы
        initializeDefaultStrategies();

        return List.of(importFileStrategy);
    }

    /**
     * Находит стратегию по имени.
     *
     * @param strategyName имя стратегии
     * @return стратегия или null, если не найдена
     */
    public FileProcessingStrategy findStrategyByName(String strategyName) {
        if (strategyName == null || strategyName.isEmpty()) {
            return null;
        }

        // Инициализируем стратегии, если еще не инициализированы
        initializeDefaultStrategies();

        return getAllStrategies().stream()
                .filter(s -> s.getName().equalsIgnoreCase(strategyName))
                .findFirst()
                .orElse(null);
    }
}