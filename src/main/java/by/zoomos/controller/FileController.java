package by.zoomos.controller;

import by.zoomos.model.entity.ProcessingStatus;
import by.zoomos.service.ProcessingStatusService;
import by.zoomos.service.file.FileProcessorService;
import by.zoomos.service.file.processor.FileProcessor;
import by.zoomos.service.file.processor.FileProcessorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Контроллер для загрузки файлов
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileProcessorService fileProcessorService;
    private final ProcessingStatusService statusService;

    /**
     * Загружает и асинхронно обрабатывает файл
     *
     * @param file файл для обработки
     * @param clientId идентификатор клиента
     * @return идентификатор статуса обработки
     */
    @PostMapping("/upload/async")
    public ResponseEntity<Long> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientId") Long clientId) {

        log.info("Получен запрос на асинхронную обработку файла: {}, размер: {}",
                file.getOriginalFilename(), file.getSize());

        CompletableFuture<Long> futureStatusId = fileProcessorService.processFileAsync(file, clientId);
        return ResponseEntity.accepted().body(futureStatusId.join());
    }

    /**
     * Загружает и синхронно обрабатывает файл
     *
     * @param file файл для обработки
     * @param clientId идентификатор клиента
     * @return статус обработки
     */
    @PostMapping("/upload")
    public ResponseEntity<ProcessingStatus> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientId") Long clientId) {

        log.info("Получен запрос на обработку файла: {}, размер: {}",
                file.getOriginalFilename(), file.getSize());

        Long statusId = fileProcessorService.processFile(file, clientId);
        ProcessingStatus status = statusService.getStatus(statusId);

        return ResponseEntity.ok(status);
    }

    /**
     * Отменяет обработку файла
     *
     * @param statusId идентификатор статуса обработки
     * @param reason причина отмены
     * @return статус операции
     */
    @PostMapping("/cancel/{statusId}")
    public ResponseEntity<Void> cancelProcessing(
            @PathVariable Long statusId,
            @RequestParam(required = false) String reason) {

        log.info("Получен запрос на отмену обработки. StatusId: {}, Reason: {}", statusId, reason);
        fileProcessorService.cancelProcessing(statusId, reason);

        return ResponseEntity.ok().build();
    }

    /**
     * Получает текущий статус обработки
     *
     * @param statusId идентификатор статуса обработки
     * @return текущий статус
     */
    @GetMapping("/status/{statusId}")
    public ResponseEntity<ProcessingStatus> getStatus(@PathVariable Long statusId) {
        log.debug("Получен запрос статуса обработки: {}", statusId);
        return ResponseEntity.ok(statusService.getStatus(statusId));
    }

    /**
     * Проверяет, поддерживается ли формат файла
     *
     * @param fileName имя файла
     * @return статус поддержки формата
     */
    @GetMapping("/check-format")
    public ResponseEntity<Boolean> checkFileFormat(@RequestParam String fileName) {
        log.debug("Проверка поддержки формата файла: {}", fileName);
        return ResponseEntity.ok(fileProcessorService.isSupportedFormat(fileName));
    }
}
