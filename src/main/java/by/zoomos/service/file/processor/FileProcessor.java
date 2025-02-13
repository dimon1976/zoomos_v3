package by.zoomos.service.file.processor;

import by.zoomos.exception.FileProcessingException;

import java.io.InputStream;

/**
 * Интерфейс для обработки файлов разных форматов
 */
public interface FileProcessor {

    /**
     * Обрабатывает файл заданного формата
     *
     * @param inputStream поток данных файла
     * @param clientId идентификатор клиента
     * @param statusId идентификатор статуса обработки
     * @throws FileProcessingException при ошибке обработки файла
     */
    void process(InputStream inputStream, Long clientId, Long statusId);

    /**
     * Проверяет, поддерживается ли данный формат файла
     *
     * @param fileName имя файла
     * @return true если формат поддерживается
     */
    boolean supports(String fileName);
}
