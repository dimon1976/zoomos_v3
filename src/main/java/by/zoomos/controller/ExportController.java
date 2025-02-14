package by.zoomos.controller;

import by.zoomos.service.export.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для экспорта данных
 */
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private final ExportService exportService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    /**
     * Экспортирует данные в выбранном формате
     */
    @GetMapping
    public ResponseEntity<Resource> exportData(
            @RequestParam Long clientId,
            @RequestParam String format,
            // Фильтры
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            // Настройки экспорта
            @RequestParam(defaultValue = "true") boolean includeRegions,
            @RequestParam(defaultValue = "true") boolean includeCompetitors,
            @RequestParam(defaultValue = "false") boolean separateSheets
    ) {
        log.info("Запрос на экспорт данных. Клиент: {}, Формат: {}", clientId, format);

        Map<String, String> params = new HashMap<>();
        params.put("clientId", clientId.toString());

        // Добавляем фильтры
        if (productId != null) params.put("productId", productId);
        if (brand != null) params.put("brand", brand);
        if (region != null) params.put("region", region);
        if (minPrice != null) params.put("minPrice", minPrice.toString());
        if (maxPrice != null) params.put("maxPrice", maxPrice.toString());
        if (startDate != null) params.put("startDate", startDate.format(DATE_FORMATTER));
        if (endDate != null) params.put("endDate", endDate.format(DATE_FORMATTER));

        // Добавляем настройки экспорта
        params.put("includeRegions", String.valueOf(includeRegions));
        params.put("includeCompetitors", String.valueOf(includeCompetitors));
        params.put("separateSheets", String.valueOf(separateSheets));

        Resource resource = exportService.exportData(clientId, format, params);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + exportService.getFileName(format) + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        exportService.getContentType(format)))
                .body(resource);
    }
}