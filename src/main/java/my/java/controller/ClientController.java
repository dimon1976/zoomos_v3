package my.java.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.ClientDto;
import my.java.dto.FileOperationDto;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import my.java.service.client.ClientService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;
    private final FileOperationRepository fileOperationRepository;

    /**
     * Отображение списка всех клиентов
     */
    @GetMapping
    public String getAllClients(Model model, HttpServletRequest request) {
        log.debug("GET request to get all clients");
        model.addAttribute("clients", clientService.getAllClients());
        model.addAttribute("currentUri", request.getRequestURI());
        return "clients/list";
    }

    /**
     * Отображение формы создания нового клиента
     */
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        log.debug("GET request to show create client form");
        model.addAttribute("client", new ClientDto());
        return "clients/form";
    }

    /**
     * Обработка создания нового клиента
     */
    @PostMapping("/create")
    public String createClient(@Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        log.debug("POST request to create a client: {}", clientDto);

        if (result.hasErrors()) {
            log.debug("Validation errors detected: {}", result.getAllErrors());
            return "clients/form";
        }

        try {
            ClientDto createdClient = clientService.createClient(clientDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + createdClient.getName() + "' успешно создан");
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            log.error("Error creating client: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    /**
     * Отображение данных клиента
     */
    @GetMapping("/{id}")
    public String getClientDetails(@PathVariable Long id,
                                   @RequestParam(required = false) String tab,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        log.debug("GET request to get client details for id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);

                    // Получаем активную операцию импорта для клиента (если есть)
                    List<FileOperation> pendingOperations = fileOperationRepository
                            .findByClientIdAndStatusIn(
                                    id,
                                    Arrays.asList(
                                            FileOperation.OperationStatus.PENDING,
                                            FileOperation.OperationStatus.PROCESSING
                                    )
                            );

                    // Добавляем самую последнюю операцию, если есть
                    if (!pendingOperations.isEmpty()) {
                        // Сортируем по времени начала (сначала новые)
                        pendingOperations.sort((op1, op2) ->
                                op2.getStartedAt().compareTo(op1.getStartedAt()));

                        FileOperation latestOperation = pendingOperations.get(0);
                        model.addAttribute("activeImportOperation", mapToDto(latestOperation));
                    }

                    // Также можем добавить недавно завершенные операции
                    List<FileOperation> recentOperations = fileOperationRepository
                            .findByClientIdAndStatusIn(
                                    id,
                                    Arrays.asList(
                                            FileOperation.OperationStatus.COMPLETED,
                                            FileOperation.OperationStatus.FAILED
                                    )
                            );

                    if (!recentOperations.isEmpty()) {
                        // Сортируем по времени начала (сначала новые)
                        recentOperations.sort((op1, op2) ->
                                op2.getStartedAt().compareTo(op1.getStartedAt()));

                        // Ограничиваем количество операций для отображения
                        List<FileOperationDto> recentOps = recentOperations.stream()
                                .limit(5)
                                .map(this::mapToDto)
                                .collect(Collectors.toList());

                        model.addAttribute("recentImportOperations", recentOps);
                    }

                    // Добавляем активную вкладку в модель, если указана
                    if (tab != null) {
                        model.addAttribute("activeTab", tab);
                    }

                    return "clients/details";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    // Метод преобразования FileOperation в FileOperationDto
    private FileOperationDto mapToDto(FileOperation operation) {
        if (operation == null) {
            return null;
        }

        return FileOperationDto.builder()
                .id(operation.getId())
                .clientId(operation.getClient() != null ? operation.getClient().getId() : null)
                .clientName(operation.getClient() != null ? operation.getClient().getName() : null)
                .operationType(operation.getOperationType())
                .fileName(operation.getFileName())
                .fileType(operation.getFileType())
                .recordCount(operation.getRecordCount())
                .status(operation.getStatus())
                .errorMessage(operation.getErrorMessage())
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .createdBy(operation.getCreatedBy())
                .processingProgress(operation.getProcessingProgress())
                .processedRecords(operation.getProcessedRecords())
                .totalRecords(operation.getTotalRecords())
                .build();
    }


    /**
     * Отображение формы редактирования клиента
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("GET request to show edit form for client id: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/form";
                })
                .orElseGet(() -> {
                    log.warn("Client not found with id: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    /**
     * Обработка обновления данных клиента
     */
    @PostMapping("/{id}/edit")
    public String updateClient(@PathVariable Long id,
                               @Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        log.debug("POST request to update client id: {}", id);

        if (result.hasErrors()) {
            log.debug("Validation errors detected: {}", result.getAllErrors());
            return "clients/form";
        }

        try {
            ClientDto updatedClient = clientService.updateClient(id, clientDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + updatedClient.getName() + "' успешно обновлен");
            return "redirect:/clients/" + id;
        } catch (EntityNotFoundException e) {
            log.error("Client not found for update: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            log.error("Error updating client: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    /**
     * Удаление клиента
     */
    @PostMapping("/{id}/delete")
    public String deleteClient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("POST request to delete client id: {}", id);

        // Получаем имя клиента перед удалением для сообщения
        String clientName = clientService.getClientById(id)
                .map(ClientDto::getName)
                .orElse("неизвестный");

        if (clientService.deleteClient(id)) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Клиент '" + clientName + "' успешно удален");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Клиент с ID " + id + " не найден");
        }

        return "redirect:/clients";
    }

    /**
     * Поиск клиентов
     */
    @GetMapping("/search")
    public String searchClients(@RequestParam String query, Model model) {
        log.debug("GET request to search clients with query: {}", query);

        model.addAttribute("clients", clientService.searchClients(query));
        model.addAttribute("searchQuery", query);
        return "clients/list";
    }
}