// src/main/java/my/java/controller/ImportController.java
package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.FieldMappingDto;
import my.java.exception.FileOperationException;
import my.java.model.FileOperation;
import my.java.service.client.ClientService;
import my.java.service.file.importer.ImportOrchestratorService;
import my.java.service.mapping.FieldMappingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Контроллер для импорта файлов
 */
@Controller
@RequestMapping("/clients/{clientId}/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final ClientService clientService;
    private final FieldMappingService fieldMappingService;
    private final ImportOrchestratorService importOrchestratorService;

    /**
     * Показать форму импорта
     */
    @GetMapping
    public String showImportForm(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show import form for client: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    // Получаем активные шаблоны для клиента
                    List<FieldMappingDto> mappings = fieldMappingService.getActiveMappingsForClient(clientId, null);

                    model.addAttribute("client", client);
                    model.addAttribute("mappings", mappings);
                    model.addAttribute("importType", "COMBINED"); // По умолчанию составной

                    return "import/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Обработать импорт файла
     */
    @PostMapping
    public String handleImport(@PathVariable Long clientId,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam("importType") String importType,
                               @RequestParam(value = "mappingId", required = false) Long mappingId,
                               RedirectAttributes redirectAttributes) {
        log.debug("POST request to import file for client: {}", clientId);

        try {
            // Валидация основных параметров
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите файл для загрузки");
                return "redirect:/clients/" + clientId + "/import";
            }

            if (mappingId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Пожалуйста, выберите шаблон маппинга");
                return "redirect:/clients/" + clientId + "/import";
            }

            // Запускаем импорт через оркестратор
            FileOperation operation = importOrchestratorService.startImport(clientId, file, mappingId);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Файл загружен и поставлен в очередь на обработку. Операция #" + operation.getId());

            // Перенаправляем на страницу статуса операции
            return "redirect:/operations/" + operation.getId() + "/status";

        } catch (FileOperationException e) {
            log.error("File operation error during import: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients/" + clientId + "/import";

        } catch (Exception e) {
            log.error("Unexpected error during file import", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Произошла неожиданная ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/import";
        }
    }

    /**
     * AJAX: Получить шаблоны для выбранного типа импорта
     */
    @GetMapping("/mappings/{importType}")
    @ResponseBody
    public List<FieldMappingDto> getMappingsForType(@PathVariable Long clientId,
                                                    @PathVariable String importType) {
        log.debug("AJAX request to get mappings for client: {} and import type: {}", clientId, importType);

        String entityType = "COMBINED".equals(importType) ? "COMBINED" : null;
        return fieldMappingService.getActiveMappingsForClient(clientId, entityType);
    }

    /**
     * Отмена импорта (если возможно)
     */
    @PostMapping("/operations/{operationId}/cancel")
    public String cancelImport(@PathVariable Long clientId,
                               @PathVariable Long operationId,
                               RedirectAttributes redirectAttributes) {
        log.debug("POST request to cancel import operation: {}", operationId);

        try {
            boolean cancelled = importOrchestratorService.cancelOperation(operationId);

            if (cancelled) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Операция импорта отменена");
            } else {
                redirectAttributes.addFlashAttribute("warningMessage",
                        "Нельзя отменить операцию, которая уже выполняется");
            }

        } catch (Exception e) {
            log.error("Error cancelling operation: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при отмене операции: " + e.getMessage());
        }

        return "redirect:/clients/" + clientId;
    }
}