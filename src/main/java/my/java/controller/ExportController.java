package my.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.repository.FileOperationRepository;
import my.java.service.client.ClientService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            @RequestParam("entityType") String entityType,
            @RequestParam("format") String format,
            @RequestParam(value = "fields[]", required = false) List<String> fields,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        log.info("POST запрос на экспорт данных для клиента: {}, тип сущности: {}, формат: {}",
                clientId, entityType, format);

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
            params.put("entityType", entityType);
            params.put("format", format);
            params.put("fields", String.join(",", fields));

            // В реальной реализации здесь должен быть вызов сервиса для экспорта
            // и создание соответствующей записи в таблице операций

            // Создаем фиктивную операцию экспорта для демонстрации
            FileOperation operation = new FileOperation();
            operation.setClient(client);
            operation.setOperationType(FileOperation.OperationType.EXPORT);
            operation.setFileName("export_" + entityType + "." + format);
            operation.setFileType(format.toUpperCase());
            operation.setStatus(FileOperation.OperationStatus.PENDING);


            FileOperation savedOperation = fileOperationRepository.save(operation);

            // Добавляем сообщение об успешном начале экспорта
            redirectAttributes.addFlashAttribute("successMessage",
                    "Экспорт данных успешно начат. ID операции: " + savedOperation.getId());

            // Перенаправляем на страницу операций
            return "redirect:/clients/" + clientId + "/operations/" + savedOperation.getId();

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

            // В реальной реализации здесь должна быть логика получения файла
            // Для демонстрации создадим фейковый файл
            // В настоящей реализации нужно использовать путь из operation.getResultFilePath()

            // Заглушка: возвращаем текстовый файл
            String content = "Это демонстрационный файл экспорта\n" +
                    "Клиент: " + client.getName() + "\n" +
                    "Тип операции: " + operation.getOperationType() + "\n" +
                    "Формат: " + operation.getFileType() + "\n";

            // Устанавливаем заголовки для скачивания файла
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + operation.getFileName() + "\"");

            // В реальной реализации здесь будет Resource для возврата файла
            // Заглушка - просто текстовый ответ вместо файла
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new org.springframework.core.io.ByteArrayResource(content.getBytes()));

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