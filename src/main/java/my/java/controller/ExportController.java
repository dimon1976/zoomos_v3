package my.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.export.ExportTemplate;
import my.java.repository.FileOperationRepository;
import my.java.service.client.ClientService;
import my.java.service.export.ExportService;
import my.java.service.export.ExportTemplateService;
import my.java.service.file.metadata.EntityRegistry;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Контроллер для экспорта данных
 */
@Controller
@RequestMapping("/clients/{clientId}/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private final ClientService clientService;
    private final FileOperationRepository fileOperationRepository;
    private final ExportService exportService;
    private final ExportTemplateService templateService;
    private final EntityRegistry entityRegistry;

    /**
     * Отображение страницы экспорта
     */
    @GetMapping
    public String showExportForm(
            @PathVariable Long clientId,
            Model model,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        log.debug("GET запрос на отображение формы экспорта для клиента ID: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("currentUri", request.getRequestURI());

                    // Добавляем список доступных шаблонов
                    List<ExportTemplate> templates = templateService.getRecentTemplates(clientId);
                    model.addAttribute("templates", templates);

                    // Добавляем список доступных форматов
                    model.addAttribute("formats", Arrays.asList("csv", "xlsx", "json", "xml"));

                    return "export/form";
                })
                .orElseGet(() -> {
                    log.warn("Клиент с ID {} не найден", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Обработка запроса на экспорт данных
     */
    @PostMapping
    public String exportData(
            @PathVariable Long clientId,
            @RequestParam(value = "templateId", required = false) Long templateId,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "fields[]", required = false) List<String> fields,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        log.info("POST запрос на экспорт данных для клиента: {}, шаблон: {}, формат: {}",
                clientId, templateId, format);

        if (fields == null || fields.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите поля для экспорта");
            return "redirect:/clients/" + clientId + "/export";
        }

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Извлекаем параметры
            Map<String, String> params = extractParams(allParams);

            FileOperation operation;

            // Экспорт с использованием существующего шаблона или создание нового
            if (templateId != null) {
                // Используем существующий шаблон
                operation = exportService.initiateExport(clientId, templateId, params);
            } else {
                // Создаем маппинг полей
                Map<String, String> fieldMapping = new HashMap<>();
                for (String field : fields) {
                    fieldMapping.put(field, field); // Для простоты используем то же имя в качестве целевого
                }

                // Инициируем экспорт
                operation = exportService.initiateExport(
                        clientId, entityType, format, fieldMapping, params, null);
            }

            // Добавляем сообщение об успешном начале экспорта
            redirectAttributes.addFlashAttribute("successMessage",
                    "Экспорт данных успешно начат. ID операции: " + operation.getId());

            // Перенаправляем на страницу операций
            return "redirect:/clients/" + clientId + "/operations/" + operation.getId();

        } catch (IllegalArgumentException e) {
            log.error("Ошибка при экспорте: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients/" + clientId + "/export";
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при экспорте: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Произошла ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export";
        }
    }

    /**
     * Сохранение текущего экспорта как шаблона
     */
    @PostMapping("/save-template")
    public String saveAsTemplate(
            @PathVariable Long clientId,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("entityType") String entityType,
            @RequestParam("format") String format,
            @RequestParam("fields[]") List<String> fields,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        log.info("POST запрос на сохранение шаблона экспорта для клиента: {}, имя: {}", clientId, name);

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Создаем маппинг полей
            Map<String, String> fieldMapping = new HashMap<>();
            for (String field : fields) {
                fieldMapping.put(field, field); // Используем те же имена полей
            }

            // Извлекаем параметры
            Map<String, String> params = extractParams(allParams);

            // Создаем шаблон
            ExportTemplate template = templateService.createTemplate(
                    name, description, client, entityType, format, fieldMapping, params, null);

            // Добавляем сообщение об успешном создании шаблона
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон экспорта успешно сохранен. ID шаблона: " + template.getId());

            return "redirect:/clients/" + clientId + "/export";

        } catch (IllegalArgumentException e) {
            log.error("Ошибка при сохранении шаблона: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients/" + clientId + "/export";
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при сохранении шаблона: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Произошла ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export";
        }
    }

    /**
     * Получение метаданных шаблона экспорта
     */
    @GetMapping("/templates/{templateId}")
    @ResponseBody
    public ResponseEntity<?> getTemplateMetadata(
            @PathVariable Long clientId,
            @PathVariable Long templateId) {

        log.debug("GET запрос на получение метаданных шаблона ID: {}", templateId);

        try {
            Optional<ExportTemplate> template = templateService.getTemplateById(templateId);

            if (template.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Проверяем, что шаблон принадлежит клиенту
            if (!template.get().getClient().getId().equals(clientId)) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Шаблон не принадлежит клиенту с ID " + clientId));
            }

            // Преобразуем в DTO для отправки на клиент
            Map<String, Object> templateData = Map.of(
                    "id", template.get().getId(),
                    "name", template.get().getName(),
                    "description", template.get().getDescription() != null ? template.get().getDescription() : "",
                    "entityType", template.get().getEntityType(),
                    "format", template.get().getFormat(),
                    "fields", template.get().getFieldMapping().keySet(),
                    "parameters", template.get().getParameters()
            );

            return ResponseEntity.ok(templateData);

        } catch (Exception e) {
            log.error("Ошибка при получении метаданных шаблона: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Удаление шаблона экспорта
     */
    @DeleteMapping("/templates/{templateId}")
    @ResponseBody
    public ResponseEntity<?> deleteTemplate(
            @PathVariable Long clientId,
            @PathVariable Long templateId) {

        log.info("DELETE запрос на удаление шаблона ID: {}", templateId);

        try {
            Optional<ExportTemplate> template = templateService.getTemplateById(templateId);

            if (template.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Проверяем, что шаблон принадлежит клиенту
            if (!template.get().getClient().getId().equals(clientId)) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Шаблон не принадлежит клиенту с ID " + clientId));
            }

            // Удаляем шаблон
            templateService.deleteTemplate(templateId);

            return ResponseEntity.ok(Map.of("message", "Шаблон успешно удален"));

        } catch (Exception e) {
            log.error("Ошибка при удалении шаблона: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Скачивание экспортированного файла
     */
    @GetMapping("/download/{operationId}")
    public ResponseEntity<Resource> downloadExportedFile(
            @PathVariable Long clientId,
            @PathVariable Long operationId,
            HttpServletResponse response) {

        log.debug("GET запрос на скачивание экспортированного файла для операции: {}", operationId);

        try {
            // Проверяем существование клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Получаем операцию
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция с ID " + operationId + " не найдена"));

            // Проверяем, что операция принадлежит клиенту
            if (!operation.getClient().getId().equals(clientId)) {
                throw new IllegalArgumentException("Операция не принадлежит данному клиенту");
            }

            // Проверяем, что операция завершена
            if (operation.getStatus() != FileOperation.OperationStatus.COMPLETED) {
                throw new FileOperationException("Операция еще не завершена");
            }

            // Проверяем наличие пути к файлу результата
            if (operation.getResultFilePath() == null || operation.getResultFilePath().isEmpty()) {
                throw new FileOperationException("Путь к файлу результата не указан");
            }

            // Создаем ресурс для файла
            org.springframework.core.io.FileSystemResource resource =
                    new org.springframework.core.io.FileSystemResource(operation.getResultFilePath());

            if (!resource.exists() || !resource.isReadable()) {
                throw new FileOperationException("Файл не существует или не доступен для чтения");
            }

            // Устанавливаем заголовки для скачивания файла
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + operation.getFileName() + "\"");

            MediaType mediaType = determineMediaType(operation.getFileName());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(mediaType)
                    .body(resource);

        } catch (IllegalArgumentException | FileOperationException e) {
            log.error("Ошибка при скачивании экспортированного файла: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при скачивании файла: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Определяет MediaType по имени файла
     */
    private MediaType determineMediaType(String fileName) {
        if (fileName.endsWith(".csv")) {
            return MediaType.parseMediaType("text/csv");
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else if (fileName.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        } else if (fileName.endsWith(".xml")) {
            return MediaType.APPLICATION_XML;
        } else {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * Извлечение параметров экспорта из всех параметров запроса
     */
    private Map<String, String> extractParams(Map<String, String> allParams) {
        Map<String, String> params = new HashMap<>();

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("params[") && entry.getKey().endsWith("]")) {
                String paramName = entry.getKey().substring(7, entry.getKey().length() - 1);
                params.put(paramName, entry.getValue());
            }
        }

        return params;
    }
}