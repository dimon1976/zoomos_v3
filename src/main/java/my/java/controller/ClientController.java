package my.java.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.ClientDto;
import my.java.service.client.ClientService;
import my.java.service.file.FileOperationMetadata;
import my.java.service.file.FileOperationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Контроллер для управления клиентами и их данными
 */
@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private static final String REDIRECT_TO_CLIENTS = "redirect:/clients";
    private static final String SUCCESS_MESSAGE_ATTR = "successMessage";
    private static final String ERROR_MESSAGE_ATTR = "errorMessage";
    private static final String CLIENT_ATTR = "client";
    private static final String CLIENTS_ATTR = "clients";

    private static final String CLIENTS_LIST_VIEW = "clients/list";
    private static final String CLIENT_FORM_VIEW = "clients/form";
    private static final String CLIENT_DETAILS_VIEW = "clients/details";
    private static final String CLIENT_OPERATIONS_VIEW = "clients/operations";
    private static final String CLIENT_IMPORT_VIEW = "clients/import";
    private static final String CLIENT_EXPORT_VIEW = "clients/export";

    private final ClientService clientService;
    private final FileOperationService fileOperationService;

    /**
     * Отображает список всех клиентов
     */
    @GetMapping
    public String getAllClients(Model model, HttpServletRequest request) {
        log.debug("GET request to show all clients");
        model.addAttribute(CLIENTS_ATTR, clientService.getAllClients());
        model.addAttribute("currentUri", request.getRequestURI());
        return CLIENTS_LIST_VIEW;
    }

    /**
     * Отображает форму создания нового клиента
     */
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        log.debug("GET request to show create client form");
        model.addAttribute(CLIENT_ATTR, new ClientDto());
        return CLIENT_FORM_VIEW;
    }

    /**
     * Обрабатывает создание нового клиента
     */
    @PostMapping("/create")
    public String createClient(
            @Valid @ModelAttribute(CLIENT_ATTR) ClientDto clientDto,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        log.debug("POST request to create a client: {}", clientDto);

        if (result.hasErrors()) {
            logValidationErrors(result);
            return CLIENT_FORM_VIEW;
        }

        try {
            ClientDto createdClient = clientService.createClient(clientDto);
            addClientCreatedSuccessMessage(redirectAttributes, createdClient);
            return REDIRECT_TO_CLIENTS;
        } catch (IllegalArgumentException e) {
            log.error("Error creating client: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return CLIENT_FORM_VIEW;
        }
    }

    /**
     * Отображает данные клиента
     */
    @GetMapping("/{id}")
    public String getClientDetails(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.debug("GET request to get client details for id: {}", id);

        return findClientAndExecute(id, client -> {
            model.addAttribute(CLIENT_ATTR, client);
            addRecentOperationsToModel(model, id);
            return CLIENT_DETAILS_VIEW;
        }, redirectAttributes);
    }

    /**
     * Отображает форму редактирования клиента
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.debug("GET request to show edit form for client id: {}", id);

        return findClientAndExecute(id, client -> {
            model.addAttribute(CLIENT_ATTR, client);
            return CLIENT_FORM_VIEW;
        }, redirectAttributes);
    }

    /**
     * Обрабатывает обновление данных клиента
     */
    @PostMapping("/{id}/edit")
    public String updateClient(
            @PathVariable Long id,
            @Valid @ModelAttribute(CLIENT_ATTR) ClientDto clientDto,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        log.debug("POST request to update client id: {}", id);

        if (result.hasErrors()) {
            logValidationErrors(result);
            return CLIENT_FORM_VIEW;
        }

        try {
            ClientDto updatedClient = clientService.updateClient(id, clientDto);
            addClientUpdatedSuccessMessage(redirectAttributes, updatedClient);
            return "redirect:/clients/" + id;
        } catch (EntityNotFoundException e) {
            log.error("Client not found for update: {}", e.getMessage());
            addErrorMessage(redirectAttributes, e.getMessage());
            return REDIRECT_TO_CLIENTS;
        } catch (IllegalArgumentException e) {
            log.error("Error updating client: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return CLIENT_FORM_VIEW;
        }
    }

    /**
     * Удаляет клиента
     */
    @PostMapping("/{id}/delete")
    public String deleteClient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("POST request to delete client id: {}", id);

        String clientName = getClientNameForDisplay(id);

        if (clientService.deleteClient(id)) {
            addClientDeletedSuccessMessage(redirectAttributes, clientName);
        } else {
            addClientNotFoundErrorMessage(redirectAttributes, id);
        }

        return REDIRECT_TO_CLIENTS;
    }

    /**
     * Осуществляет поиск клиентов
     */
    @GetMapping("/search")
    public String searchClients(@RequestParam String query, Model model) {
        log.debug("GET request to search clients with query: {}", query);

        model.addAttribute(CLIENTS_ATTR, clientService.searchClients(query));
        model.addAttribute("searchQuery", query);
        return CLIENTS_LIST_VIEW;
    }

    /**
     * Отображает страницу операций клиента
     */
    @GetMapping("/{id}/operations")
    public String getClientOperations(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.debug("GET request to show operations for client id: {}", id);

        return findClientAndExecute(id, client -> {
            model.addAttribute(CLIENT_ATTR, client);
            addPaginatedOperationsToModel(model, id, startDate, endDate, type, status, page, size);
            return CLIENT_OPERATIONS_VIEW;
        }, redirectAttributes);
    }

    /**
     * Отображает страницу импорта файлов
     */
    @GetMapping("/{id}/import")
    public String showImportPage(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.debug("GET request to show import page for client id: {}", id);

        return findClientAndExecute(id, client -> {
            model.addAttribute(CLIENT_ATTR, client);
            return CLIENT_IMPORT_VIEW;
        }, redirectAttributes);
    }

    /**
     * Отображает страницу экспорта данных
     */
    @GetMapping("/{id}/export")
    public String showExportPage(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.debug("GET request to show export page for client id: {}", id);

        return findClientAndExecute(id, client -> {
            model.addAttribute(CLIENT_ATTR, client);
            return CLIENT_EXPORT_VIEW;
        }, redirectAttributes);
    }

    /**
     * Обрабатывает запрос на экспорт данных
     */
    @PostMapping("/{id}/export/generate")
    public String processExport(
            @PathVariable Long id,
            @RequestParam String dataType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam String fileFormat,
            @RequestParam(required = false, defaultValue = "false") boolean includeHeaders,
            @RequestParam(required = false, defaultValue = "false") boolean zipFiles,
            RedirectAttributes redirectAttributes) {

        log.debug("POST request to export data for client id: {}", id);

        try {
            Long operationId = fileOperationService.initiateExport(
                    id, dataType, startDate, endDate, fileFormat, includeHeaders, zipFiles);

            addExportSuccessMessage(redirectAttributes, operationId);
            return "redirect:/clients/" + id + "/operations";
        } catch (Exception e) {
            log.error("Error during export request: {}", e.getMessage());
            addExportErrorMessage(redirectAttributes, e.getMessage());
            return "redirect:/clients/" + id + "/export";
        }
    }

    // Вспомогательные методы

    /**
     * Выполняет действие с найденным клиентом или возвращает перенаправление при ошибке
     */
    private String findClientAndExecute(
            Long id,
            ClientAction action,
            RedirectAttributes redirectAttributes) {

        return clientService.getClientById(id)
                .map(action::execute)
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    addClientNotFoundErrorMessage(redirectAttributes, id);
                    return REDIRECT_TO_CLIENTS;
                });
    }

    /**
     * Добавляет недавние операции клиента в модель
     */
    private void addRecentOperationsToModel(Model model, Long clientId) {
        List<FileOperationMetadata> recentOperations =
                fileOperationService.getRecentOperationsForClient(clientId, 5);
        model.addAttribute("recentOperations", recentOperations);
    }

    /**
     * Добавляет пагинированные операции клиента в модель
     */
    private void addPaginatedOperationsToModel(
            Model model,
            Long clientId,
            LocalDate startDate,
            LocalDate endDate,
            String type,
            String status,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<FileOperationMetadata> operationsPage =
                fileOperationService.getClientOperations(clientId, startDate, endDate, type, status, pageable);

        model.addAttribute("operations", operationsPage.getContent());
        model.addAttribute("currentPage", operationsPage.getNumber());
        model.addAttribute("totalPages", operationsPage.getTotalPages());
        model.addAttribute("size", size);
    }

    /**
     * Получает имя клиента для отображения (или "неизвестный" если клиент не найден)
     */
    private String getClientNameForDisplay(Long id) {
        return clientService.getClientById(id)
                .map(ClientDto::getName)
                .orElse("неизвестный");
    }

    /**
     * Логирует ошибки валидации
     */
    private void logValidationErrors(BindingResult result) {
        log.debug("Validation errors detected: {}", result.getAllErrors());
    }

    /**
     * Добавляет сообщение об успешном создании клиента
     */
    private void addClientCreatedSuccessMessage(RedirectAttributes redirectAttributes, ClientDto client) {
        redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE_ATTR,
                "Клиент '" + client.getName() + "' успешно создан");
    }

    /**
     * Добавляет сообщение об успешном обновлении клиента
     */
    private void addClientUpdatedSuccessMessage(RedirectAttributes redirectAttributes, ClientDto client) {
        redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE_ATTR,
                "Клиент '" + client.getName() + "' успешно обновлен");
    }

    /**
     * Добавляет сообщение об успешном удалении клиента
     */
    private void addClientDeletedSuccessMessage(RedirectAttributes redirectAttributes, String clientName) {
        redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE_ATTR,
                "Клиент '" + clientName + "' успешно удален");
    }

    /**
     * Добавляет сообщение об ошибке - клиент не найден
     */
    private void addClientNotFoundErrorMessage(RedirectAttributes redirectAttributes, Long id) {
        redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTR,
                "Клиент с ID " + id + " не найден");
    }

    /**
     * Добавляет сообщение об ошибке
     */
    private void addErrorMessage(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTR, message);
    }

    /**
     * Добавляет сообщение об успешном запросе на экспорт
     */
    private void addExportSuccessMessage(RedirectAttributes redirectAttributes, Long operationId) {
        redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE_ATTR,
                "Запрос на экспорт данных успешно создан. Операция #" + operationId);
    }

    /**
     * Добавляет сообщение об ошибке при экспорте
     */
    private void addExportErrorMessage(RedirectAttributes redirectAttributes, String errorMessage) {
        redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTR,
                "Ошибка при создании запроса на экспорт: " + errorMessage);
    }

    /**
     * Интерфейс для действия с клиентом
     */
    @FunctionalInterface
    private interface ClientAction {
        String execute(ClientDto client);
    }
}