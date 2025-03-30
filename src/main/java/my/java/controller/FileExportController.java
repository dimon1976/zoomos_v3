package my.java.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.model.Client;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.RegionData;
import my.java.service.client.ClientService;
import my.java.service.competitor.CompetitorDataService;
import my.java.service.file.exporter.FileExportService;
import my.java.service.file.exporter.FileFormat;
import my.java.service.file.exporter.tracker.ExportProgressTracker;
import my.java.service.product.ProductService;
import my.java.service.region.RegionDataService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для операций экспорта файлов
 * Путь: /java/my/java/controller/FileExportController.java
 */
@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
@Slf4j
public class FileExportController {

    private final FileExportService exportService;
    private final ClientService clientService;
    private final ProductService productService;
    private final RegionDataService regionDataService;
    private final CompetitorDataService competitorDataService;
    private final ExportProgressTracker progressTracker;

    // Карта соответствия строковых типов сущностей и классов
    private static final Map<String, Class<? extends ImportableEntity>> ENTITY_TYPES = Map.of(
            "product", Product.class,
            "competitor", Competitor.class,
            "region", RegionData.class
    );

    /**
     * Показывает страницу экспорта
     */
    @GetMapping
    public String showExportPage(Model model) {
        log.debug("Запрос на отображение страницы экспорта");
        model.addAttribute("clients", clientService.getAllClients());
        return "export/index";
    }

    /**
     * Экспортирует данные указанного типа сущности
     */
    @GetMapping("/entity/{entityType}")
    public void exportEntityData(
            @PathVariable String entityType,
            @RequestParam Long clientId,
            @RequestParam(required = false, defaultValue = "CSV") String format,
            @RequestParam(required = false) Map<String, Object> filters,
            HttpServletResponse response) throws IOException {

        log.info("Запрос на экспорт данных типа {} для клиента {}, формат: {}",
                entityType, clientId, format);

        // Получаем клиента
        Client client = clientService.findClientEntityById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + clientId));

        // Получаем класс сущности
        Class<? extends ImportableEntity> entityClass = ENTITY_TYPES.get(entityType.toLowerCase());
        if (entityClass == null) {
            throw new IllegalArgumentException("Неподдерживаемый тип сущности: " + entityType);
        }

        // Настраиваем заголовки ответа
        FileFormat fileFormat = FileFormat.fromString(format);
        String filename = generateFilename(entityType, client.getName(), fileFormat.getExtension());

        response.setContentType(fileFormat.getContentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        // Выполняем экспорт
        Map<String, Object> filterCriteria = new HashMap<>(filters);
        filterCriteria.put("clientId", clientId);

        try {
            exportService.exportData(
                    entityClass,
                    client,
                    filterCriteria,
                    response.getOutputStream(),
                    format,
                    entityType);
        } catch (Exception e) {
            log.error("Ошибка при экспорте данных: {}", e.getMessage(), e);
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Ошибка при экспорте данных: " + e.getMessage());
        }
    }

    /**
     * Экспортирует данные на основе ранее выполненного импорта
     */
    @GetMapping("/from-import/{importOperationId}")
    public void exportFromImport(
            @PathVariable Long importOperationId,
            @RequestParam(required = false, defaultValue = "CSV") String format,
            HttpServletResponse response) throws IOException {

        log.info("Запрос на экспорт данных из импорта {}, формат: {}",
                importOperationId, format);

        // Настраиваем заголовки ответа
        FileFormat fileFormat = FileFormat.fromString(format);
        String filename = "export_from_import_" + importOperationId + "." + fileFormat.getExtension();

        response.setContentType(fileFormat.getContentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        // Выполняем экспорт
        try {
            exportService.exportDataByImportOperation(
                    importOperationId,
                    response.getOutputStream(),
                    format);
        } catch (Exception e) {
            log.error("Ошибка при экспорте данных из импорта: {}", e.getMessage(), e);
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Ошибка при экспорте данных: " + e.getMessage());
        }
    }

    /**
     * Запускает асинхронный экспорт данных
     */
    @PostMapping("/api/async/{entityType}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startAsyncExport(
            @PathVariable String entityType,
            @RequestParam Long clientId,
            @RequestParam(required = false, defaultValue = "CSV") String format,
            @RequestParam(required = false) List<String> fields, // Добавляем параметр полей
            @RequestParam(required = false) Map<String, Object> filters) {

        log.info("Запрос на асинхронный экспорт данных типа {} для клиента {}, формат: {}, выбранных полей: {}",
                entityType, clientId, format, fields != null ? fields.size() : "все");

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + clientId));

            // Получаем класс сущности
            Class<? extends ImportableEntity> entityClass = ENTITY_TYPES.get(entityType.toLowerCase());
            if (entityClass == null) {
                throw new IllegalArgumentException("Неподдерживаемый тип сущности: " + entityType);
            }

            // Создаем потоки для временного хранения данных
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Создаем параметры экспорта
            Map<String, Object> exportParams = new HashMap<>();

            // Добавляем базовые параметры
            exportParams.put("clientId", clientId);

            // Добавляем дополнительные фильтры, если они есть
            if (filters != null && !filters.isEmpty()) {
                exportParams.putAll(filters);
            }

            // Добавляем выбранные поля, если они указаны
            if (fields != null && !fields.isEmpty()) {
                exportParams.put("includedFields", fields);
            }

            // Запускаем экспорт в отдельном потоке
            new Thread(() -> {
                try {
                    FileOperationDto operation = exportService.exportData(
                            entityClass,
                            client,
                            exportParams,
                            outputStream,
                            format,
                            entityType);

                    log.info("Асинхронный экспорт завершен, ID операции: {}", operation.getId());
                } catch (Exception e) {
                    log.error("Ошибка при асинхронном экспорте: {}", e.getMessage(), e);
                }
            }).start();

            // Возвращаем информацию о запущенном экспорте
            Map<String, Object> response = new HashMap<>();
            response.put("status", "started");
            response.put("message", "Экспорт данных запущен");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при запуске асинхронного экспорта: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Ошибка при запуске экспорта: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Получает статус операции экспорта
     */
    @GetMapping("/api/status/{operationId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getExportStatus(@PathVariable Long operationId) {
        log.debug("Запрос статуса экспорта для операции: {}", operationId);

        try {
            // Получаем информацию о прогрессе
            Map<String, Object> progressInfo = progressTracker.getProgressInfo(operationId);

            // Если информации о прогрессе нет в кэше, запрашиваем из БД
            if (progressInfo.isEmpty()) {
                FileOperationDto operation = exportService.getExportStatus(operationId);

                progressInfo.put("operationId", operation.getId());
                progressInfo.put("status", operation.getStatus().toString());
                progressInfo.put("progress", operation.getProcessingProgress());
                progressInfo.put("processed", operation.getProcessedRecords());
                progressInfo.put("total", operation.getTotalRecords());
                progressInfo.put("completedAt", operation.getCompletedAt());
            }

            return ResponseEntity.ok(progressInfo);
        } catch (Exception e) {
            log.error("Ошибка при получении статуса экспорта: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Ошибка при получении статуса: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Отменяет операцию экспорта
     */
    @PostMapping("/api/cancel/{operationId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelExport(@PathVariable Long operationId) {
        log.info("Запрос на отмену экспорта для операции: {}", operationId);

        try {
            boolean cancelled = exportService.cancelExport(operationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", cancelled);
            response.put("message", cancelled ? "Экспорт успешно отменен" : "Не удалось отменить экспорт");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при отмене экспорта: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ошибка при отмене экспорта: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Экспортирует конкретные записи по их идентификаторам
     */
    @PostMapping("/api/selected/{entityType}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> exportSelectedEntities(
            @PathVariable String entityType,
            @RequestParam Long clientId,
            @RequestParam List<Long> ids,
            @RequestParam(required = false, defaultValue = "CSV") String format) {

        log.info("Запрос на экспорт {} выбранных записей типа {} для клиента {}, формат: {}",
                ids.size(), entityType, clientId, format);

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + clientId));

            // Загружаем выбранные сущности в зависимости от типа
            List<? extends ImportableEntity> entities;

            switch (entityType.toLowerCase()) {
                case "product":
                    entities = productService.findByIds(ids);
                    entities = null;
                    break;
                case "competitor":
                    entities = competitorDataService.findByIds(ids);
                    entities = null;
                    break;
                case "region":
                    entities = regionDataService.findByIds(ids);
                    entities = null;
                    break;
                default:
                    throw new IllegalArgumentException("Неподдерживаемый тип сущности: " + entityType);
            }

            if (entities.isEmpty()) {
                throw new IllegalArgumentException("Не найдено записей с указанными идентификаторами");
            }

            // Создаем временный поток для данных
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Выполняем экспорт
            FileOperationDto operation = exportService.exportEntities(
                    entities,
                    client,
                    outputStream,
                    format,
                    entityType);

            // Формируем ответ
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Экспорт успешно выполнен");
            response.put("operationId", operation.getId());
            response.put("dataSize", outputStream.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при экспорте выбранных записей: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ошибка при экспорте: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Генерирует имя файла для экспорта
     */
    private String generateFilename(String entityType, String clientName, String extension) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String sanitizedClientName = clientName.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_");

        return String.format(
                "%s_%s_%s.%s",
                entityType.toLowerCase(),
                sanitizedClientName,
                timestamp,
                extension
        );
    }
}