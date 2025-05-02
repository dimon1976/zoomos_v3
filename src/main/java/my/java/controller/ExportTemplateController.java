// src/main/java/my/java/controller/ExportTemplateController.java
package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.ExportTemplate;
import my.java.service.client.ClientService;
import my.java.service.file.exporter.ExportTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/clients/{clientId}/export/templates")
@RequiredArgsConstructor
@Slf4j
public class ExportTemplateController {

    private final ClientService clientService;
    private final ExportTemplateService templateService;

    /**
     * Отображение списка шаблонов
     */
    @GetMapping
    public String listTemplates(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        return clientService.getClientById(clientId)
                .map(client -> {
                    model.addAttribute("client", client);
                    List<ExportTemplate> templates = templateService.getAllTemplatesForClient(clientId);
                    model.addAttribute("templates", templates);
                    return "export/templates/list";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Клиент не найден: " + clientId);
                    return "redirect:/clients";
                });
    }

    /**
     * Отображение формы создания/редактирования шаблона
     */
    @GetMapping("/{id}")
    public String editTemplate(@PathVariable Long clientId, @PathVariable Long id,
                               Model model, RedirectAttributes redirectAttributes) {

        return templateService.getTemplateById(id)
                .map(template -> {
                    model.addAttribute("template", template);
                    model.addAttribute("client", template.getClient());
                    return "export/templates/edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Шаблон не найден: " + id);
                    return "redirect:/clients/" + clientId + "/export/templates";
                });
    }

    /**
     * Сохранение шаблона
     */
    @PostMapping("/save")
    public String saveTemplate(@PathVariable Long clientId,
                               @ModelAttribute ExportTemplate template,
                               RedirectAttributes redirectAttributes) {

        try {
            // Проверяем существование клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + clientId));

            template.setClient(client);
            templateService.saveTemplate(template);

            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно сохранен");
            return "redirect:/clients/" + clientId + "/export/templates";
        } catch (Exception e) {
            log.error("Ошибка при сохранении шаблона: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
            return "redirect:/clients/" + clientId + "/export/templates";
        }
    }

    /**
     * Удаление шаблона
     */
    @PostMapping("/{id}/delete")
    public String deleteTemplate(@PathVariable Long clientId, @PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {

        try {
            templateService.deleteTemplate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Шаблон успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении шаблона: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: " + e.getMessage());
        }

        return "redirect:/clients/" + clientId + "/export/templates";
    }

    /**
     * API для загрузки доступных шаблонов
     */
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<?> getTemplatesForEntity(@PathVariable Long clientId,
                                                   @RequestParam String entityType) {
        try {
            List<ExportTemplate> templates = templateService.getTemplatesForEntityType(clientId, entityType);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            log.error("Ошибка при загрузке шаблонов: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * API для загрузки шаблона по ID
     */
    @GetMapping("/api/{templateId}")
    @ResponseBody
    public ResponseEntity<?> getTemplateById(@PathVariable Long clientId,
                                             @PathVariable Long templateId) {
        try {
            return templateService.getTemplateById(templateId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Ошибка при загрузке шаблона: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}