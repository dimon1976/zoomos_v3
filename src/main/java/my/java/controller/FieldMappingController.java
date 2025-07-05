package my.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.ClientDto;
import my.java.dto.FieldMappingDto;
import my.java.dto.FieldMappingDto.FieldMappingDetailDto;
import my.java.service.client.ClientService;
import my.java.service.mapping.FieldMappingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для управления шаблонами маппинга полей
 */
@Controller
@RequestMapping("/clients/{clientId}/mappings")
@RequiredArgsConstructor
@Slf4j
public class FieldMappingController {

    private final FieldMappingService fieldMappingService;
    private final ClientService clientService;

    /**
     * Список шаблонов клиента
     */
    @GetMapping
    public String listMappings(@PathVariable Long clientId, Model model,
                               HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.debug("GET request to list field mappings for client: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    model.addAttribute("mappings", fieldMappingService.getAllMappingsForClient(clientId));
                    model.addAttribute("currentUri", request.getRequestURI());
                    return "mappings/list";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Форма создания нового шаблона
     */
    @GetMapping("/create")
    public String showCreateForm(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show create mapping form for client: {}", clientId);

        return clientService.getClientById(clientId)
                .map(client -> {
                    FieldMappingDto mappingDto = new FieldMappingDto();
                    mappingDto.setClientId(clientId);
                    mappingDto.setImportType("COMBINED"); // По умолчанию составной
                    mappingDto.setEntityType("COMBINED");
                    mappingDto.setFileEncoding("UTF-8");
                    mappingDto.setCsvDelimiter(";");
                    mappingDto.setCsvQuoteChar("\"");
                    mappingDto.setDuplicateStrategy("SKIP");
                    mappingDto.setIsActive(true);

                    model.addAttribute("client", client);
                    model.addAttribute("mapping", mappingDto);
                    model.addAttribute("availableFields",
                            fieldMappingService.getAvailableFieldsForMapping("COMBINED"));
                    return "mappings/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + clientId + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Создание нового шаблона
     */
    @PostMapping("/create")
    public String createMapping(@PathVariable Long clientId,
                                @Valid @ModelAttribute("mapping") FieldMappingDto mappingDto,
                                @RequestParam(value = "detailSourceField", required = false) List<String> sourceFields,
                                @RequestParam(value = "detailTargetField", required = false) List<String> targetFields,
                                @RequestParam(value = "detailTargetEntity", required = false) List<String> targetEntities,
                                @RequestParam(value = "detailRequired", required = false) List<String> requiredFields,
                                BindingResult result,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.debug("POST request to create mapping for client: {}", clientId);

        if (result.hasErrors()) {
            log.debug("Validation errors: {}", result.getAllErrors());
            ClientDto client = clientService.getClientById(clientId).orElse(null);
            model.addAttribute("client", client);
            model.addAttribute("availableFields",
                    fieldMappingService.getAvailableFieldsForMapping(mappingDto.getEntityType()));
            return "mappings/form";
        }

        try {
            // Формируем детали маппинга
            mappingDto.setDetails(buildMappingDetails(sourceFields, targetFields, targetEntities, requiredFields));
            mappingDto.setClientId(clientId);

            FieldMappingDto createdMapping = fieldMappingService.createMapping(mappingDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + createdMapping.getName() + "' успешно создан");
            return "redirect:/clients/" + clientId + "/mappings";

        } catch (Exception e) {
            log.error("Error creating mapping: {}", e.getMessage());
            result.rejectValue("name", "error.mapping", e.getMessage());

            ClientDto client = clientService.getClientById(clientId).orElse(null);
            model.addAttribute("client", client);
            model.addAttribute("availableFields",
                    fieldMappingService.getAvailableFieldsForMapping(mappingDto.getEntityType()));
            return "mappings/form";
        }
    }

    /**
     * Форма редактирования шаблона
     */
    @GetMapping("/{mappingId}/edit")
    public String showEditForm(@PathVariable Long clientId,
                               @PathVariable Long mappingId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        log.debug("GET request to show edit form for mapping: {}", mappingId);

        return fieldMappingService.getMappingById(mappingId)
                .map(mapping -> {
                    ClientDto client = clientService.getClientById(clientId).orElse(null);
                    model.addAttribute("client", client);
                    model.addAttribute("mapping", mapping);
                    model.addAttribute("availableFields",
                            fieldMappingService.getAvailableFieldsForMapping(mapping.getEntityType()));
                    return "mappings/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Шаблон с ID " + mappingId + " не найден");
                    return "redirect:/clients/" + clientId + "/mappings";
                });
    }

    /**
     * Обновление шаблона
     */
    @PostMapping("/{mappingId}/edit")
    public String updateMapping(@PathVariable Long clientId,
                                @PathVariable Long mappingId,
                                @Valid @ModelAttribute("mapping") FieldMappingDto mappingDto,
                                @RequestParam(value = "detailSourceField", required = false) List<String> sourceFields,
                                @RequestParam(value = "detailTargetField", required = false) List<String> targetFields,
                                @RequestParam(value = "detailTargetEntity", required = false) List<String> targetEntities,
                                @RequestParam(value = "detailRequired", required = false) List<String> requiredFields,
                                BindingResult result,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        log.debug("POST request to update mapping: {}", mappingId);

        if (result.hasErrors()) {
            ClientDto client = clientService.getClientById(clientId).orElse(null);
            model.addAttribute("client", client);
            model.addAttribute("availableFields",
                    fieldMappingService.getAvailableFieldsForMapping(mappingDto.getEntityType()));
            return "mappings/form";
        }

        try {
            // Формируем детали маппинга
            mappingDto.setDetails(buildMappingDetails(sourceFields, targetFields, targetEntities, requiredFields));

            FieldMappingDto updatedMapping = fieldMappingService.updateMapping(mappingId, mappingDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + updatedMapping.getName() + "' успешно обновлен");
            return "redirect:/clients/" + clientId + "/mappings";

        } catch (Exception e) {
            log.error("Error updating mapping: {}", e.getMessage());
            result.rejectValue("name", "error.mapping", e.getMessage());

            ClientDto client = clientService.getClientById(clientId).orElse(null);
            model.addAttribute("client", client);
            model.addAttribute("availableFields",
                    fieldMappingService.getAvailableFieldsForMapping(mappingDto.getEntityType()));
            return "mappings/form";
        }
    }

    /**
     * Удаление шаблона
     */
    @PostMapping("/{mappingId}/delete")
    public String deleteMapping(@PathVariable Long clientId,
                                @PathVariable Long mappingId,
                                RedirectAttributes redirectAttributes) {
        log.debug("POST request to delete mapping: {}", mappingId);

        String mappingName = fieldMappingService.getMappingById(mappingId)
                .map(FieldMappingDto::getName)
                .orElse("неизвестный");

        if (fieldMappingService.deleteMapping(mappingId)) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаблон '" + mappingName + "' успешно удален");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Шаблон с ID " + mappingId + " не найден");
        }

        return "redirect:/clients/" + clientId + "/mappings";
    }

    /**
     * AJAX: Получить доступные поля для типа сущности
     */
    @GetMapping("/fields/{entityType}")
    @ResponseBody
    public Map<String, Map<String, String>> getAvailableFields(@PathVariable String entityType) {
        log.debug("AJAX request to get available fields for entity type: {}", entityType);
        return fieldMappingService.getAvailableFieldsForMapping(entityType);
    }

    /**
     * Формирование списка деталей маппинга из параметров формы
     */
    private List<FieldMappingDetailDto> buildMappingDetails(List<String> sourceFields,
                                                            List<String> targetFields,
                                                            List<String> targetEntities,
                                                            List<String> requiredFields) {
        List<FieldMappingDetailDto> details = new ArrayList<>();

        if (sourceFields != null && targetFields != null) {
            for (int i = 0; i < sourceFields.size() && i < targetFields.size(); i++) {
                String sourceField = sourceFields.get(i);
                String targetField = targetFields.get(i);

                if (sourceField != null && !sourceField.trim().isEmpty() &&
                        targetField != null && !targetField.trim().isEmpty()) {

                    FieldMappingDetailDto detail = new FieldMappingDetailDto();
                    detail.setSourceField(sourceField.trim());
                    detail.setTargetField(targetField.trim());
                    detail.setOrderIndex(i);

                    // Устанавливаем целевую сущность
                    if (targetEntities != null && i < targetEntities.size()) {
                        detail.setTargetEntity(targetEntities.get(i));
                    }

                    // Проверяем, обязательное ли поле
                    detail.setRequired(requiredFields != null && requiredFields.contains(String.valueOf(i)));

                    details.add(detail);
                }
            }
        }

        return details;
    }
}