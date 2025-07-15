package my.java.service.file.importer.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import my.java.service.file.importer.DuplicateStrategy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DuplicateHandlingStrategyFactory {

    private final List<DuplicateHandlingStrategy> strategies;
    private Map<DuplicateStrategy, DuplicateHandlingStrategy> strategyMap;

    @PostConstruct
    private void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(DuplicateHandlingStrategy::getType, Function.identity()));
    }

    public DuplicateHandlingStrategy getStrategy(DuplicateStrategy type) {
        DuplicateHandlingStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown strategy: " + type);
        }
        return strategy;
    }
}
