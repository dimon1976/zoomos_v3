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

    // Кеш эмиттеров для отправки SSE-событий (ключ - operationId, значение - Map<emitterId, emitter>)
    private final Map<Long, Map<String, SseEmitter>> operationEmitters = new ConcurrentHashMap<>();

    /**
     * Отображение страницы загрузки файла
     */
    @GetMapping("/upload")
    public String showUploadForm(@RequestParam Long clientId, Model model, HttpServletRequest request) {
        log.debug("GET запрос на отображение формы загрузки для клиента ID: {}", clientId);

        model.addAttribute("clientId", clientId);
        model.addAttribute("currentUri", request.getRequestURI());

        return "files/upload";
    }

    /**
     * Обработка загрузки файла
     */
    @PostMapping("/upload")
    public String uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientId") Long clientId,
            @RequestParam(value = "operationType", defaultValue = "IMPORT") String operationType,
            @RequestParam(value = "entityType", required = false) String entityType,
            RedirectAttributes redirectAttributes) {

        log.debug("POST запрос на загрузку файла: {}, ID клиента: {}, тип операции: {}, тип сущности: {}",
                file.getOriginalFilename(), clientId, operationType, entityType);

        try {
            // Инициализируем операцию обработки файла
            Long operationId = initializeFileOperation(file, clientId, operationType);

            // Сохраняем тип сущности в метаданных
            saveEntityTypeToMetadata(operationId, entityType);

            // Перенаправляем на страницу деталей операции
            redirectAttributes.addFlashAttribute("successMessage",
                    "Файл успешно загружен. Начата обработка.");

            return "redirect:/files/operations/" + operationId;
        } catch (FileOperationException e) {
            return handleFileOperationException(e, clientId, redirectAttributes);
        } catch (Exception e) {
            return handleUnexpectedException(e, clientId, redirectAttributes);
        }
    }

    /**
     * Инициализирует операцию обработки файла
     */
    private Long initializeFileOperation(MultipartFile file, Long clientId, String operationType)
            throws FileOperationException {
        OperationType opType = OperationType.valueOf(operationType);
        return fileProcessingService.initializeFileOperation(file, clientId, opType);
    }

    /**
     * Сохраняет тип сущности в метаданных операции
     */
    private void saveEntityTypeToMetadata(Long operationId, String entityType) {
        if (entityType != null && !entityType.isEmpty()) {
            FileOperationMetadata metadata = FileOperationMetadata.get(operationId);
            if (metadata != null) {
                metadata.addParam("entityType", entityType);
            }
        }
    }

    /**
     * Обрабатывает исключение FileOperationException
     */
    private String handleFileOperationException(FileOperationException e, Long clientId,
                                                RedirectAttributes redirectAttributes) {
        log.error("Ошибка при загрузке файла: {}", e.getMessage(), e);
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return "redirect:/clients/" + clientId;
    }

    /**
     * Обрабатывает непредвиденное исключение
     */
    private String handleUnexpectedException(Exception e, Long clientId,
                                             RedirectAttributes redirectAttributes) {
        log.error("Непредвиденная ошибка при загрузке файла: {}", e.getMessage(), e);
        redirectAttributes.addFlashAttribute("errorMessage",
                "Произошла ошибка при загрузке файла: " + e.getMessage());
        return "redirect:/clients/" + clientId;
    }

    /**
     * Отображение деталей операции
     */
    @GetMapping("/operations/{id}")
    public String getOperationDetails(@PathVariable Long id, Model model) {
        log.debug("GET запрос на получение деталей операции для ID: {}", id);

        try {
            // Получаем статус операции
            FileOperationStatus status = fileProcessingService.getOperationStatus(id);
            model.addAttribute("operation", status);
            model.addAttribute("operationId", id);

            return "files/operation-details";
        } catch (Exception e) {
            log.error("Ошибка при получении деталей операции: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при получении деталей операции: " + e.getMessage());
            return "error/general";
        }
    }

    /**
     * Отмена операции
     */
    @PostMapping("/operations/{id}/cancel")
    public String cancelOperation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("POST запрос на отмену операции ID: {}", id);

        try {
            boolean canceled = fileProcessingService.cancelOperation(id);
            setCancelOperationResultMessage(canceled, redirectAttributes);
            return "redirect:/files/operations/" + id;
        } catch (Exception e) {
            return handleCancelOperationException(e, id, redirectAttributes);
        }
    }

    /**
     * Устанавливает сообщение о результате отмены операции
     */
    private void setCancelOperationResultMessage(boolean canceled, RedirectAttributes redirectAttributes) {
        if (canceled) {
            redirectAttributes.addFlashAttribute("successMessage", "Операция успешно отменена");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось отменить операцию");
        }
    }

    /**
     * Обрабатывает исключение при отмене операции
     */
    private String handleCancelOperationException(Exception e, Long id, RedirectAttributes redirectAttributes) {
        log.error("Ошибка при отмене операции: {}", e.getMessage(), e);
        redirectAttributes.addFlashAttribute("errorMessage",
                "Ошибка при отмене операции: " + e.getMessage());
        return "redirect:/files/operations/" + id;
    }

    /**
     * Получение статуса операции через AJAX
     */
    @GetMapping("/operations/{id}/status")
    @ResponseBody
    public ResponseEntity<FileOperationStatus> getOperationStatus(@PathVariable Long id) {
        log.debug("AJAX запрос на получение статуса операции для ID: {}", id);

        try {
            FileOperationStatus status = fileProcessingService.getOperationStatus(id);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Ошибка при получении статуса операции: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Подписка на обновления статуса операции через Server-Sent Events
     */
    @GetMapping(value = "/operations/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter trackProgress(@PathVariable Long id) {
        log.debug("SSE запрос на отслеживание прогресса для операции ID: {}", id);

        SseEmitter emitter = createLongLivedEmitter();
        String emitterId = generateUniqueEmitterId();

        registerEmitter(id, emitterId, emitter);
        sendInitialStatus(id, emitter);
        configureEmitterCallbacks(id, emitterId, emitter);

        return emitter;
    }

    /**
     * Создает эмиттер с длительным сроком жизни
     */
    private SseEmitter createLongLivedEmitter() {
        return new SseEmitter(Long.MAX_VALUE);
    }

    /**
     * Генерирует уникальный идентификатор для эмиттера
     */
    private String generateUniqueEmitterId() {
        return "emitter_" + System.currentTimeMillis();
    }

    /**
     * Регистрирует эмиттер в кеше
     */
    private void registerEmitter(Long operationId, String emitterId, SseEmitter emitter) {
        operationEmitters.computeIfAbsent(operationId, k -> new ConcurrentHashMap<>())
                .put(emitterId, emitter);
    }

    /**
     * Отправляет начальный статус операции через эмиттер
     */
    private void sendInitialStatus(Long operationId, SseEmitter emitter) {
        try {
            FileOperationStatus status = fileProcessingService.getOperationStatus(operationId);
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(status));
        } catch (Exception e) {
            log.error("Ошибка отправки начального SSE события: {}", e.getMessage());
        }
    }

    /**
     * Настраивает обработчики событий для эмиттера
     */
    private void configureEmitterCallbacks(Long operationId, String emitterId, SseEmitter emitter) {
        emitter.onCompletion(() -> {
            log.debug("SSE эмиттер завершен для операции ID: {}", operationId);
            removeEmitter(operationId, emitterId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE эмиттер тайм-аут для операции ID: {}", operationId);
            removeEmitter(operationId, emitterId);
        });

        emitter.onError((e) -> {
            log.error("SSE эмиттер ошибка для операции ID: {}: {}", operationId, e.getMessage());
            removeEmitter(operationId, emitterId);
        });
    }

    /**
     * Запуск обработки операции
     */
    @PostMapping("/operations/{id}/process")
    @ResponseBody
    public ResponseEntity<FileOperationStatus> startProcessing(
            @PathVariable Long id,
            @RequestParam(value = "entityType", required = false) String entityType) {
        log.debug("POST запрос на начало обработки для операции ID: {}, тип сущности: {}", id, entityType);

        try {
            // Определяем класс сущности на основе типа
            Class<?> entityClass = determineEntityClass(entityType);

            // Запускаем асинхронную обработку
            FileOperationStatus status = asyncFileProcessingService.startProcessing(id, entityClass);

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Ошибка при запуске обработки: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Отправляет обновление статуса всем подписчикам операции
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
                if (isOperationCompleted(status)) {
                    emitter.complete();
                }
            } catch (Exception e) {
                handleEmitterSendError(operationId, id, emitter, e);
            }
        });
    }

    /**
     * Проверяет, завершена ли операция
     */
    private boolean isOperationCompleted(FileOperationStatus status) {
        return status.getStatus().toString().equals("COMPLETED") ||
                status.getStatus().toString().equals("FAILED");
    }

    /**
     * Обрабатывает ошибку отправки через эмиттер
     */
    private void handleEmitterSendError(Long operationId, String emitterId, SseEmitter emitter, Exception e) {
        log.error("Ошибка отправки SSE обновления эмиттеру {}: {}", emitterId, e.getMessage());
        emitter.completeWithError(e);
        removeEmitter(operationId, emitterId);
    }

    /**
     * Удаляет эмиттер из кеша
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
     */
    private Class<?> determineEntityClass(String entityType) throws ClassNotFoundException {
        if (entityType == null || entityType.isEmpty()) {
            // По умолчанию используем Product
            return Class.forName("my.java.model.entity.Product");
        }

        switch (entityType.toLowerCase()) {
            case "product":
                return Class.forName("my.java.model.entity.Product");
            case "competitor":
                return Class.forName("my.java.model.entity.CompetitorData");
            case "region":
                return Class.forName("my.java.model.entity.RegionData");
            default:
                throw new IllegalArgumentException("Неподдерживаемый тип сущности: " + entityType);
        }
    }
}