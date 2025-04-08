package my.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FileOperationDto;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.service.client.ClientService;
import my.java.service.file.entity.CompositeEntityService;
import my.java.service.file.importer.FileImportService;
import my.java.service.file.mapping.FieldMappingServiceEnhanced;
import my.java.service.file.metadata.EntityRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Контроллер для импорта файлов с поддержкой составных сущностей
 */
@Controller
@RequestMapping("/clients/{clientId}/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final ClientService clientService;
    private final FileImportService fileImportService;
    private final FieldMappingServiceEnhanced fieldMappingService;
    private final EntityRegistry entityRegistry;

    /**
     * Отображение страницы импорта
     */
    @GetMapping
    public String showImportForm(
            @PathVariable Long clientId,
            Model model,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        log.debug("GET запрос на отображение формы импорта для клиента ID: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("currentUri", request.getRequestURI());
                    return "import/form";
                })
                .orElseGet(() -> {
                    log.warn("Клиент с ID {} не найден", clientId);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Обработка загрузки и импорта файла
     */
    @PostMapping
    public String importFile(
            @PathVariable Long clientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("entityType") String entityType,
            @RequestParam(value = "mappingId", required = false) Long mappingId,
            @RequestParam(value = "composite", required = false, defaultValue = "false") boolean isComposite,
            @RequestParam Map<String, String> allParams,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes)
    {
        log.info("=========== ДЕТАЛЬНАЯ ОТЛАДКА ИМПОРТА ===========");
        log.info("Время запроса: {}", new Date());
        log.info("IP адрес: {}", request.getRemoteAddr());
        log.info("User-Agent: {}", request.getHeader("User-Agent"));

        // 1. Базовые параметры
        log.info("--- Основные параметры ---");
        log.info("clientId: {}", clientId);
        log.info("entityType: {}", entityType);
        log.info("mappingId: {}", mappingId);
        log.info("composite: {}", isComposite);

        // 2. Информация о файле
        log.info("--- Информация о файле ---");
        log.info("Имя файла: {}", file != null ? file.getOriginalFilename() : "null");
        log.info("Размер: {}", file != null ? file.getSize() + " байт" : "null");
        log.info("Тип контента: {}", file != null ? file.getContentType() : "null");
        log.info("Пустой?: {}", file != null ? file.isEmpty() : "null");

        // 3. Все параметры запроса
        log.info("--- Все параметры запроса ---");
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            log.info("{} = {}", entry.getKey(), entry.getValue());
        }

        // 4. Проверка параметров params[...]
        log.info("--- Извлеченные параметры конфигурации ---");
        Map<String, String> extractedParams = new HashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("params[") && entry.getKey().endsWith("]")) {
                String paramName = entry.getKey().substring(7, entry.getKey().length() - 1);
                extractedParams.put(paramName, entry.getValue());
            }
        }

        if (extractedParams.isEmpty()) {
            log.warn("Не найдены параметры в формате params[ключ]");
        } else {
            for (Map.Entry<String, String> entry : extractedParams.entrySet()) {
                log.info("params[{}] = {}", entry.getKey(), entry.getValue());
            }
        }

        // 5. Проверка заголовков запроса
        log.info("--- Заголовки запроса ---");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            log.info("{}: {}", headerName, request.getHeader(headerName));
        }

        // 6. Проверка атрибутов запроса
        log.info("--- Атрибуты запроса ---");
        Enumeration<String> attributeNames = request.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String attrName = attributeNames.nextElement();
            log.info("{} = {}", attrName, request.getAttribute(attrName));
        }

        // Теперь продолжаем обычную обработку импорта...

        log.info("POST запрос на импорт файла для клиента: {}, тип сущности: {}, составной: {}",
                clientId, entityType, isComposite);

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите файл для загрузки");
            return "redirect:/clients/" + clientId + "/import";
        }
        if (entityType != null && entityType.contains(",")) {
            entityType = entityType.split(",")[0];
            log.info("Преобразованный параметр entityType: {}", entityType);
        }

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Извлекаем параметры
            Map<String, String> params = extractParams(allParams);
            params.put("entityType", entityType);
            params.put("composite", String.valueOf(isComposite));

            // Если выбран составной импорт, добавляем список связанных сущностей
            if (isComposite) {
                List<String> relatedEntities = getRelatedEntities(entityType);
                if (!relatedEntities.isEmpty()) {
                    params.put("relatedEntities", String.join(",", relatedEntities));
                }
            }

            // Получаем маппинг полей
            Map<String, String> fieldMapping = null;
            if (mappingId != null) {
                fieldMapping = fieldMappingService.getMappingById(mappingId);
            }

            // Начинаем асинхронный импорт
            CompletableFuture<FileOperationDto> futureOperation =
                    fileImportService.importFileAsync(file, client, mappingId, null, params, isComposite);

            // Получаем ID операции
            FileOperationDto operation = futureOperation.getNow(null);

            if (operation != null && operation.getId() != null) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Файл успешно загружен и импорт начат. ID операции: " + operation.getId());
                return "redirect:/clients/" + clientId + "/operations/" + operation.getId();
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Файл успешно загружен и импорт начат.");
                return "redirect:/clients/" + clientId + "/operations";
            }

        } catch (IllegalArgumentException e) {
            log.error("Ошибка при импорте: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients/" + clientId + "/import";
        } catch (FileOperationException e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при обработке файла: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/import";
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при импорте: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Произошла ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/import";
        }
    }

    /**
     * API для анализа файла перед импортом
     */
    @PostMapping("/analyze")
    @ResponseBody
    public ResponseEntity<?> analyzeFile(
            @PathVariable Long clientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("analysisEntityType") String entityType,
            @RequestParam(value = "composite", required = false, defaultValue = "false") boolean isComposite) {

        log.debug("POST запрос на анализ файла для клиента: {}, тип: {}, составной: {}",
                clientId, entityType, isComposite);

        try {
            // Проверяем существование клиента
            clientService.getClientById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Анализируем файл
            Map<String, Object> result = fileImportService.analyzeFile(file);
            result.put("entityType", entityType);
            result.put("isComposite", isComposite);

            // Если выбран составной импорт, добавляем информацию о связанных сущностях
            if (isComposite) {
                List<String> relatedEntities = getRelatedEntities(entityType);
                result.put("relatedEntities", relatedEntities);
            }

            // Получаем доступные маппинги для данного типа сущности
            result.put("availableMappings", fieldMappingService.getAvailableMappingsForClient(clientId, entityType));

            // Получаем метаданные полей для правильного отображения таблицы предпросмотра
            if (isComposite) {
                result.put("fieldsMetadata", fieldMappingService.getCompositeEntityFieldsMetadata(entityType));
            } else {
                var entityMetadata = entityRegistry.getEntityMetadata(entityType);
                if (entityMetadata != null) {
                    result.put("fieldsMetadata", Map.of(
                            "entityType", entityType,
                            "displayName", entityMetadata.getDisplayName(),
                            "fields", entityMetadata.getPrefixedFields()
                    ));
                }
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Ошибка при анализе файла: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Получение связанных сущностей для основной сущности
     */
    private List<String> getRelatedEntities(String mainEntityType) {
        var relatedEntities = entityRegistry.getRelatedEntities(mainEntityType);
        return relatedEntities.stream()
                .map(entity -> entity.getEntityType())
                .toList();
    }

    /**
     * Извлечение параметров импорта из всех параметров запроса
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