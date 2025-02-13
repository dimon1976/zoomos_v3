package by.zoomos.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Контроллер для доступа к метрикам мониторинга
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@Slf4j
public class MonitoringController {

    private final MeterRegistry meterRegistry;

    /**
     * Получает метрики производительности
     *
     * @return карта метрик
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Double>> getPerformanceMetrics() {
        log.debug("Запрос метрик производительности");
        Map<String, Double> metrics = new HashMap<>();

        // Метрики обработки файлов
        Search.in(meterRegistry)
                .name("file.processing")
                .timers()
                .forEach(timer ->
                        metrics.put("file_processing_" + timer.getId().getTag("method"),
                                timer.mean(TimeUnit.MILLISECONDS)));

        // Метрики экспорта
        Search.in(meterRegistry)
                .name("file.export")
                .timers()
                .forEach(timer ->
                        metrics.put("file_export_" + timer.getId().getTag("method"),
                                timer.mean(TimeUnit.MILLISECONDS)));

        return ResponseEntity.ok(metrics);
    }

    /**
     * Получает метрики операций
     *
     * @param type тип операции (success/error)
     * @return карта метрик
     */
    @GetMapping("/operations")
    public ResponseEntity<Map<String, Double>> getOperationMetrics(
            @RequestParam(defaultValue = "success") String type) {
        log.debug("Запрос метрик операций типа: {}", type);
        Map<String, Double> metrics = new HashMap<>();

        Search.in(meterRegistry)
                .name("operations." + type)
                .counters()
                .forEach(counter ->
                        metrics.put(counter.getId().getTag("method"), counter.count()));

        return ResponseEntity.ok(metrics);
    }

    /**
     * Получает все доступные метрики
     *
     * @return карта всех метрик
     */
    @GetMapping
    public ResponseEntity<Map<String, Map<String, Double>>> getAllMetrics() {
        log.debug("Запрос всех метрик");
        Map<String, Map<String, Double>> allMetrics = new HashMap<>();

        // Получаем метрики производительности
        Map<String, Double> performanceMetrics = new HashMap<>();
        Search.in(meterRegistry)
                .name("file")
                .timers()
                .forEach(timer ->
                        performanceMetrics.put(
                                timer.getId().getName() + "_" + timer.getId().getTag("method"),
                                timer.mean(TimeUnit.MILLISECONDS)));
        allMetrics.put("performance", performanceMetrics);

        // Получаем метрики успешных операций
        Map<String, Double> successMetrics = new HashMap<>();
        Search.in(meterRegistry)
                .name("operations.success")
                .counters()
                .forEach(counter ->
                        successMetrics.put(counter.getId().getTag("method"),
                                counter.count()));
        allMetrics.put("success", successMetrics);

        // Получаем метрики ошибок
        Map<String, Double> errorMetrics = new HashMap<>();
        Search.in(meterRegistry)
                .name("operations.error")
                .counters()
                .forEach(counter ->
                        errorMetrics.put(counter.getId().getTag("method"),
                                counter.count()));
        allMetrics.put("errors", errorMetrics);

        return ResponseEntity.ok(allMetrics);
    }
}