package by.zoomos.service.file;

import by.zoomos.exception.FileProcessingException;
import by.zoomos.model.entity.ProcessingStatus;
import by.zoomos.service.ProcessingStatusService;
import by.zoomos.service.file.processor.FileProcessor;
import by.zoomos.service.file.processor.FileProcessorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис для обработки файлов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileProcessorService {

    private final FileProcessorFactory fileProcessorFactory;
    private final ProcessingStatusService statusService;

    /**
     * Асинхронно обрабатывает загруженный файл
     *
     * @param file файл для обработки
     * @param clientId идентификатор клиента
     * @return CompletableFuture с идентификатором статуса обработки
     */
    @Async("fileProcessingExecutor")
    public CompletableFuture<Long> processFileAsync(MultipartFile file, Long clientId) {
        String fileName = file.getOriginalFilename();
        log.info("Начало асинхронной обработки файла: {}", fileName);

        // Создаем запись о статусе обработки
        ProcessingStatus status = statusService.createStatus(
                clientId,
                fileName,
                file.getSize()
        );

        try {
            // Получаем подходящий процессор для файла
            FileProcessor processor = fileProcessorFactory.getProcessor(file);

            // Обрабатываем файл
            processor.process(file.getInputStream(), clientId, status.getId());

            // Помечаем обработку как успешно завершенную
            statusService.markCompleted(status.getId());
            log.info("Файл успешно обработан: {}", fileName);

            return CompletableFuture.completedFuture(status.getId());

        } catch (IOException e) {
            String errorMessage = String.format("Ошибка чтения файла %s: %s", fileName, e.getMessage());
            log.error(errorMessage, e);
            statusService.markFailed(status.getId(), errorMessage);
            throw new FileProcessingException(errorMessage, e);

        } catch (Exception e) {
            String errorMessage = String.format("Ошибка обработки файла %s: %s", fileName, e.getMessage());
            log.error(errorMessage, e);
            statusService.markFailed(status.getId(), errorMessage);
            throw new FileProcessingException(errorMessage, e);
        }
    }

    /**
     * Синхронно обрабатывает файл
     *
     * @param file файл для обработки
     * @param clientId идентификатор клиента
     * @return идентификатор статуса обработки
     */
    public Long processFile(MultipartFile file, Long clientId) {
        try {
            return processFileAsync(file, clientId).get();
        } catch (Exception e) {
            throw new FileProcessingException("Ошибка при обработке файла", e);
        }
    }

    /**
     * Отменяет обработку файла
     *
     * @param statusId идентификатор статуса обработки
     * @param reason причина отмены
     */
    public void cancelProcessing(Long statusId, String reason) {
        log.info("Запрос на отмену обработки файла. StatusId: {}, Reason: {}", statusId, reason);
        ProcessingStatus status = statusService.getStatus(statusId);

        if (status.getStatus() == ProcessingStatus.Status.PROCESSING ||
                status.getStatus() == ProcessingStatus.Status.PENDING) {
            status.markCancelled(reason);
            // Здесь можно добавить логику для остановки текущей обработки
        } else {
            log.warn("Невозможно отменить обработку файла в статусе: {}", status.getStatus());
            throw new IllegalStateException("Невозможно отменить обработку файла в текущем статусе");
        }
    }

    /**
     * Проверяет, поддерживается ли формат файла
     *
     * @param fileName имя файла
     * @return true если формат поддерживается
     */
    public boolean isSupportedFormat(String fileName) {
        return fileProcessorFactory.isSupported(fileName);
    }
}