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

    @GetMapping
    public String getAllClients(Model model, HttpServletRequest request) {
        log.debug("Получение списка всех клиентов");
        model.addAttribute("clients", clientService.getAllClients());
        model.addAttribute("currentUri", request.getRequestURI());
        return "clients/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        log.debug("Отображение формы создания клиента");
        model.addAttribute("client", new ClientDto());
        return "clients/form";
    }

    @PostMapping("/create")
    public String createClient(@Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        log.debug("Создание нового клиента: {}", clientDto);

        if (result.hasErrors()) {
            log.debug("Обнаружены ошибки валидации: {}", result.getAllErrors());
            return "clients/form";
        }

        try {
            ClientDto createdClient = clientService.createClient(clientDto);
            addSuccessMessage(redirectAttributes, "Клиент '" + createdClient.getName() + "' успешно создан");
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            log.error("Ошибка при создании клиента: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    @GetMapping("/{id}")
    public String getClientDetails(@PathVariable Long id,
                                   @RequestParam(required = false) String tab,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Получение информации о клиенте с ID: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);

                    addActiveOperationToModel(id, model);
                    addRecentOperationsToModel(id, model);

                    if (tab != null) {
                        model.addAttribute("activeTab", tab);
                    }

                    return "clients/details";
                })
                .orElseGet(() -> {
                    logClientNotFound(id);
                    addErrorMessage(redirectAttributes, "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    private void logClientNotFound(Long id) {
        log.warn("Клиент с ID {} не найден", id);
    }

    private void addActiveOperationToModel(Long clientId, Model model) {
        List<FileOperation> pendingOperations = fileOperationRepository
                .findByClientIdAndStatusIn(
                        clientId,
                        Arrays.asList(
                                FileOperation.OperationStatus.PENDING,
                                FileOperation.OperationStatus.PROCESSING
                        )
                );

        if (!pendingOperations.isEmpty()) {
            pendingOperations.sort((op1, op2) ->
                    op2.getStartedAt().compareTo(op1.getStartedAt()));

            FileOperation latestOperation = pendingOperations.get(0);
            model.addAttribute("activeImportOperation", mapToDto(latestOperation));
        }
    }

    private void addRecentOperationsToModel(Long clientId, Model model) {
        List<FileOperation> recentOperations = fileOperationRepository
                .findByClientIdAndStatusIn(
                        clientId,
                        Arrays.asList(
                                FileOperation.OperationStatus.COMPLETED,
                                FileOperation.OperationStatus.FAILED
                        )
                );

        if (!recentOperations.isEmpty()) {
            recentOperations.sort((op1, op2) ->
                    op2.getStartedAt().compareTo(op1.getStartedAt()));

            List<FileOperationDto> recentOps = recentOperations.stream()
                    .limit(5)
                    .map(this::mapToDto)
                    .collect(Collectors.toList());

            model.addAttribute("recentImportOperations", recentOps);
        }
    }

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

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Отображение формы редактирования клиента с ID: {}", id);

        return clientService.getClientById(id)
                .map(client -> {
                    model.addAttribute("client", client);
                    return "clients/form";
                })
                .orElseGet(() -> {
                    logClientNotFound(id);
                    addErrorMessage(redirectAttributes, "Клиент с ID " + id + " не найден");
                    return "redirect:/clients";
                });
    }

    @PostMapping("/{id}/edit")
    public String updateClient(@PathVariable Long id,
                               @Valid @ModelAttribute("client") ClientDto clientDto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        log.debug("Обновление клиента с ID: {}", id);

        if (result.hasErrors()) {
            log.debug("Обнаружены ошибки валидации: {}", result.getAllErrors());
            return "clients/form";
        }

        try {
            ClientDto updatedClient = clientService.updateClient(id, clientDto);
            addSuccessMessage(redirectAttributes, "Клиент '" + updatedClient.getName() + "' успешно обновлен");
            return "redirect:/clients/" + id;
        } catch (EntityNotFoundException e) {
            log.error("Клиент для обновления не найден: {}", e.getMessage());
            addErrorMessage(redirectAttributes, e.getMessage());
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            log.error("Ошибка при обновлении клиента: {}", e.getMessage());
            result.rejectValue("name", "error.client", e.getMessage());
            return "clients/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteClient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("Удаление клиента с ID: {}", id);

        String clientName = clientService.getClientById(id)
                .map(ClientDto::getName)
                .orElse("неизвестный");

        if (clientService.deleteClient(id)) {
            addSuccessMessage(redirectAttributes, "Клиент '" + clientName + "' успешно удален");
        } else {
            addErrorMessage(redirectAttributes, "Клиент с ID " + id + " не найден");
        }

        return "redirect:/clients";
    }

    @GetMapping("/search")
    public String searchClients(@RequestParam String query, Model model) {
        log.debug("Поиск клиентов по запросу: {}", query);

        model.addAttribute("clients", clientService.searchClients(query));
        model.addAttribute("searchQuery", query);
        return "clients/list";
    }

    private void addSuccessMessage(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("successMessage", message);
    }

    private void addErrorMessage(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("errorMessage", message);
    }
}