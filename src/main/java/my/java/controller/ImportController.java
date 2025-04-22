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
        // Логирование остается без изменений

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

            // Создаем FileReadingOptions напрямую из allParams
            FileReadingOptions options = FileReadingOptions.fromMap(allParams);

            // Добавляем необходимые параметры, если они отсутствуют
            options.getAdditionalParams().put("entityType", entityType);
            options.getAdditionalParams().put("composite", String.valueOf(isComposite));

            // Если выбран составной импорт, добавляем список связанных сущностей
            if (isComposite) {
                List<String> relatedEntities = getRelatedEntities(entityType);
                if (!relatedEntities.isEmpty()) {
                    options.getAdditionalParams().put("relatedEntities", String.join(",", relatedEntities));
                }
            }

            // Начинаем асинхронный импорт с использованием FileReadingOptions
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

        } catch (Exception e) {
            // Обработка ошибок остается без изменений
            log.error("Ошибка при импорте: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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

            // Создаем и настраиваем FileReadingOptions
            FileReadingOptions options = configureOptions(allParams, entityType, isComposite);

            // Анализируем файл
            Map<String, Object> result = fileImportService.analyzeFileWithOptions(file, options);
            result.put("entityType", entityType);
            result.put("isComposite", isComposite);

            // Добавляем дополнительную информацию для UI
            enrichAnalysisResult(result, clientId, entityType, isComposite, options);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка при анализе файла: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Приватный вспомогательный метод для настройки FileReadingOptions
    private FileReadingOptions configureOptions(Map<String, String> allParams, String entityType, boolean isComposite) {
        // Создаем FileReadingOptions из всех параметров
        FileReadingOptions options = FileReadingOptions.fromMap(allParams);

        // Добавляем основные параметры
        options.getAdditionalParams().put("entityType", entityType);
        options.getAdditionalParams().put("composite", String.valueOf(isComposite));

        // Если составной импорт, добавляем связанные сущности
        if (isComposite) {
            List<String> relatedEntities = getRelatedEntities(entityType);
            if (!relatedEntities.isEmpty()) {
                options.getAdditionalParams().put("relatedEntities", String.join(",", relatedEntities));
            }
        }

        return options;
    }

    // Приватный вспомогательный метод для обогащения результата анализа
    private void enrichAnalysisResult(Map<String, Object> result, Long clientId, String entityType,
                                      boolean isComposite, FileReadingOptions options) {
        // Добавляем маппинги
        result.put("availableMappings", fieldMappingService.getAvailableMappingsForClient(clientId, entityType));

        // Добавляем метаданные полей
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

        // Для составного импорта добавляем связанные сущности
        if (isComposite) {
            result.put("relatedEntities", getRelatedEntities(entityType));
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
}