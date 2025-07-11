// src/main/java/my/java/service/file/importer/strategy/DuplicateStrategyFactory.java
package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import my.java.repository.CompetitorRepository;
import my.java.repository.ProductRepository;
import my.java.repository.RegionRepository;
import my.java.service.file.importer.BatchEntityProcessor;
import org.springframework.stereotype.Component;

/**
 * Фабрика для создания стратегий обработки дубликатов
 */
@Component
@RequiredArgsConstructor
public class DuplicateStrategyFactory {

    private final BatchEntityProcessor batchEntityProcessor;
    private final ProductRepository productRepository;
    private final CompetitorRepository competitorRepository;
    private final RegionRepository regionRepository;

    /**
     * Создает стратегию по типу
     */
    public DuplicateHandlingStrategy createStrategy(String strategyType) {
        return switch (strategyType.toUpperCase()) {
            case "SKIP" -> new SkipDuplicatesStrategy(batchEntityProcessor);
            case "OVERRIDE" -> new OverrideDuplicatesStrategy(
                    batchEntityProcessor,
                    productRepository,
                    competitorRepository,
                    regionRepository
            );
            case "IGNORE" -> new IgnoreDuplicatesStrategy(batchEntityProcessor);
            default -> throw new IllegalArgumentException("Unknown strategy type: " + strategyType);
        };
    }
}