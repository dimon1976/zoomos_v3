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
import my.java.service.file.metadata.EntityMetadata;
import my.java.service.file.metadata.EntityRegistry;
import my.java.service.file.options.FileReadingOptions;
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
        log.debug("=========== ДЕТАЛЬНАЯ ОТЛАДКА ИМПОРТА ===========");
        log.debug("Время запроса: {}", new Date());
        log.debug("IP адрес: {}", request.getRemoteAddr());
        log.debug("User-Agent: {}", request.getHeader("User-Agent"));

        // 1. Базовые параметры
        log.debug("--- Основные параметры ---");
        log.debug("clientId: {}", clientId);
        log.debug("entityType: {}", entityType);
        log.debug("mappingId: {}", mappingId);
        log.debug("composite: {}", isComposite);

        // 2. Информация о файле
        log.debug("--- Информация о файле ---");
        log.debug("Имя файла: {}", file != null ? file.getOriginalFilename() : "null");
        log.debug("Размер: {}", file != null ? file.getSize() + " байт" : "null");
        log.debug("Тип контента: {}", file != null ? file.getContentType() : "null");
        log.debug("Пустой?: {}", file != null ? file.isEmpty() : "null");

        // 3. Все параметры запроса
        log.debug("--- Все параметры запроса ---");
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            log.info("{} = {}", entry.getKey(), entry.getValue());
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

            // Извлекаем параметры из формы
            Map<String, String> params = extractParams(allParams);
            params.put("entityType", entityType);
            params.put("composite", String.valueOf(isComposite));

            // Создаем объект FileReadingOptions из параметров
            FileReadingOptions options = FileReadingOptions.fromMap(params);

            // Если выбран составной импорт, добавляем список связанных сущностей
            if (isComposite) {
                List<String> relatedEntities = getRelatedEntities(entityType);
                if (!relatedEntities.isEmpty()) {
                    String relatedEntitiesStr = String.join(",", relatedEntities);
                    options.getAdditionalParams().put("relatedEntities", relatedEntitiesStr);
                }
            }

            // Получаем маппинг полей
            Map<String, String> fieldMapping = null;
            if (mappingId != null) {
                fieldMapping = fieldMappingService.getMappingById(mappingId);
            }

            // Начинаем асинхронный импорт с использованием FileReadingOptions
            // Здесь нужно обновить сигнатуру метода в сервисе
            CompletableFuture<FileOperationDto> futureOperation =
                    fileImportService.importFileAsyncWithOptions(file, client, mappingId, null, options);

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
            @RequestParam(value = "composite", required = false, defaultValue = "false") boolean isComposite,
            @RequestParam Map<String, String> allParams) {

        log.debug("POST запрос на анализ файла для клиента: {}, тип: {}, составной: {}",
                clientId, entityType, isComposite);

        try {
            // Проверяем существование клиента
            clientService.getClientById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

            // Извлекаем параметры и создаем FileReadingOptions
            Map<String, String> params = extractParams(allParams);
            params.put("entityType", entityType);
            params.put("composite", String.valueOf(isComposite));

            FileReadingOptions options = FileReadingOptions.fromMap(params);

            // Анализируем файл с использованием FileReadingOptions
            Map<String, Object> result = fileImportService.analyzeFileWithOptions(file, options);
            result.put("entityType", entityType);
            result.put("isComposite", isComposite);

            // Если выбран составной импорт, добавляем информацию о связанных сущностях
            if (isComposite) {
                List<String> relatedEntities = getRelatedEntities(entityType);
                result.put("relatedEntities", relatedEntities);

                // Добавляем связанные сущности в options
                String relatedEntitiesStr = String.join(",", relatedEntities);
                options.getAdditionalParams().put("relatedEntities", relatedEntitiesStr);
            }

            // Получаем доступные маппинги для данного типа сущности
            result.put("availableMappings", fieldMappingService.getAvailableMappingsForClient(clientId, entityType));

            // Получаем метаданные полей для правильного отображения таблицы предпросмотра
            if (isComposite) {
                // Используем метод с FileReadingOptions
                result.put("fieldsMetadata", fieldMappingService.getCompositeEntityFieldsMetadataWithOptions(entityType, options));
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
                .map(EntityMetadata::getEntityType)
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