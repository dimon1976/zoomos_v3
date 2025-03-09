package my.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.FileOperation.OperationType;
import my.java.service.file.AsyncFileProcessingService;
import my.java.service.file.FileOperationMetadata;
import my.java.service.file.FileProcessingService;
import my.java.service.file.FileProcessingService.FileOperationStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Контроллер для управления операциями обработки файлов
 */
@Controller
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileOperationController {

    private final FileProcessingService fileProcessingService;
    private final AsyncFileProcessingService asyncFileProcessingService;

    // Кеш эмиттеров для отправки SSE-событий
    private final Map<Long, Map<String, SseEmitter>> operationEmitters = new ConcurrentHashMap<>();

    /**
     * Отображение страницы загрузки файла
     */
    @GetMapping("/upload")
    public String showUploadForm(@RequestParam Long clientId,
                                 Model model,
                                 HttpServletRequest request) {
        log.debug("GET request to show upload form for client ID: {}", clientId);

        model.addAttribute("clientId", clientId);
        model.addAttribute("currentUri", request.getRequestURI());

        return "files/upload";
    }

    /**
     * Обработка загрузки файла
     */
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam("clientId") Long clientId,
                             @RequestParam(value = "operationType", defaultValue = "IMPORT") String operationType,
                             @RequestParam(value = "entityType", required = false) String entityType,
                             RedirectAttributes redirectAttributes) {

        log.debug("POST request to upload file: {}, client ID: {}, operation type: {}, entity type: {}",
                file.getOriginalFilename(), clientId, operationType, entityType);

        try {
            // Инициализируем операцию обработки файла
            OperationType opType = OperationType.valueOf(operationType);
            Long operationId = fileProcessingService.initializeFileOperation(file, clientId, opType);

            // Сохраняем тип сущности в метаданных
            if (entityType != null && !entityType.isEmpty()) {
                FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
                if (metadata != null) {
                    metadata.addParam("entityType", entityType);
                }
            }

            // Перенаправляем на страницу деталей операции
            redirectAttributes.addFlashAttribute("successMessage",
                    "Файл успешно загружен. Начата обработка.");

            return "redirect:/files/operations/" + operationId;
        } catch (FileOperationException e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/clients/" + clientId;
        } catch (Exception e) {
            log.error("Unexpected error uploading file: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Произошла ошибка при загрузке файла: " + e.getMessage());
            return "redirect:/clients/" + clientId;
        }
    }

    /**
     * Отображение деталей операции
     */
    @GetMapping("/operations/{id}")
    public String getOperationDetails(@PathVariable Long id, Model model) {
        log.debug("GET request to get operation details for ID: {}", id);

        try {
            // Получаем статус операции
            FileOperationStatus status = fileProcessingService.getOperationStatus(id);
            model.addAttribute("operation", status);
            model.addAttribute("operationId", id);

            return "files/operation-details";
        } catch (Exception e) {
            log.error("Error getting operation details: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при получении деталей операции: " + e.getMessage());
            return "error/general";
        }
    }

    /**
     * Отмена операции
     */
    @PostMapping("/operations/{id}/cancel")
    public String cancelOperation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("POST request to cancel operation ID: {}", id);

        try {
            boolean canceled = fileProcessingService.cancelOperation(id);

            if (canceled) {
                redirectAttributes.addFlashAttribute("successMessage", "Операция успешно отменена");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Не удалось отменить операцию");
            }

            return "redirect:/files/operations/" + id;
        } catch (Exception e) {
            log.error("Error canceling operation: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при отмене операции: " + e.getMessage());
            return "redirect:/files/operations/" + id;
        }
    }

    /**
     * Получение статуса операции через AJAX
     */
    @GetMapping("/operations/{id}/status")
    @ResponseBody
    public ResponseEntity<FileOperationStatus> getOperationStatus(@PathVariable Long id) {
        log.debug("AJAX request to get operation status for ID: {}", id);

        try {
            FileOperationStatus status = fileProcessingService.getOperationStatus(id);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting operation status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Подписка на обновления статуса операции через Server-Sent Events
     */
    @GetMapping(value = "/operations/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter trackProgress(@PathVariable Long id) {
        log.debug("SSE request to track progress for operation ID: {}", id);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // Генерируем уникальный ID для эмиттера
        String emitterId = "emitter_" + System.currentTimeMillis();

        // Регистрируем эмиттер
        operationEmitters.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                .put(emitterId, emitter);

        // Отправляем начальное событие
        try {
            FileOperationStatus status = fileProcessingService.getOperationStatus(id);
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(status));
        } catch (Exception e) {
            log.error("Error sending initial SSE event: {}", e.getMessage());
        }

        // Настраиваем обработчики завершения и таймаута
        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed for operation ID: {}", id);
            removeEmitter(id, emitterId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for operation ID: {}", id);
            removeEmitter(id, emitterId);
        });

        emitter.onError((e) -> {
            log.error("SSE emitter error for operation ID: {}: {}", id, e.getMessage());
            removeEmitter(id, emitterId);
        });

        return emitter;
    }

    /**
     * Запуск обработки операции
     */
    @PostMapping("/operations/{id}/process")
    @ResponseBody
    public ResponseEntity<FileOperationStatus> startProcessing(@PathVariable Long id,
                                                               @RequestParam(value = "entityType", required = false) String entityType) {
        log.debug("POST request to start processing for operation ID: {}, entity type: {}", id, entityType);

        try {
            // Определяем класс сущности на основе типа
            Class<?> entityClass = determineEntityClass(entityType);

            // Запускаем асинхронную обработку
            FileOperationStatus status = asyncFileProcessingService.startProcessing(id, entityClass);

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error starting processing: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Отправляет обновление статуса всем подписчикам операции
     *
     * @param operationId идентификатор операции
     * @param status статус операции
     */
    public void sendStatusUpdate(Long operationId, FileOperationStatus status) {
        Map<String, SseEmitter> emitters = operationEmitters.getOrDefault(operationId, Collections.emptyMap());

        if (emitters.isEmpty()) {
            return;
        }

        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(status));

                // Если операция завершена, закрываем эмиттер
                if (status.getStatus().toString().equals("COMPLETED") ||
                        status.getStatus().toString().equals("FAILED")) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("Error sending SSE update to emitter {}: {}", id, e.getMessage());
                emitter.completeWithError(e);
                removeEmitter(operationId, id);
            }
        });
    }

    /**
     * Удаляет эмиттер из кеша
     *
     * @param operationId идентификатор операции
     * @param emitterId идентификатор эмиттера
     */
    private void removeEmitter(Long operationId, String emitterId) {
        Map<String, SseEmitter> emitters = operationEmitters.get(operationId);
        if (emitters != null) {
            emitters.remove(emitterId);

            if (emitters.isEmpty()) {
                operationEmitters.remove(operationId);
            }
        }
    }

    /**
     * Определяет класс сущности на основе типа
     *
     * @param entityType тип сущности
     * @return класс сущности
     * @throws ClassNotFoundException если класс не найден
     */
    private Class<?> determineEntityClass(String entityType) throws ClassNotFoundException {
        if (entityType == null || entityType.isEmpty()) {
            // По умолчанию используем Product
            return Class.forName("by.zoomos_v2.model.entity.Product");
        }

        switch (entityType.toLowerCase()) {
            case "product":
                return Class.forName("by.zoomos_v2.model.entity.Product");
            case "competitor":
                return Class.forName("by.zoomos_v2.model.entity.CompetitorData");
            case "region":
                return Class.forName("by.zoomos_v2.model.entity.RegionData");
            default:
                throw new IllegalArgumentException("Неподдерживаемый тип сущности: " + entityType);
        }
    }
}