package by.zoomos.controller;

import by.zoomos.service.export.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Экспортирует данные в выбранный формат
     *
     * @param clientId идентификатор клиента
     * @param format формат экспорта (xlsx, csv, xls)
     * @param params дополнительные параметры экспорта
     * @return файл с экспортированными данными
     */
    @GetMapping
    public ResponseEntity<Resource> exportData(
            @RequestParam Long clientId,
            @RequestParam String format,
            @RequestParam(required = false) Map<String, String> params) {

        log.info("Запрос на экспорт данных. ClientId: {}, Format: {}", clientId, format);

        Resource resource = exportService.exportData(clientId, format, params);
        String filename = exportService.getFileName(format);
        String contentType = exportService.getContentType(format);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(resource);
    }
}
