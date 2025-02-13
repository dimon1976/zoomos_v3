package by.zoomos.exception;

/**
 * Исключение, выбрасываемое при ошибках обработки файла
 */
public class FileProcessingException extends RuntimeException {

    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileProcessingException(String message, String fileName) {
        super(String.format("%s. Файл: %s", message, fileName));
    }

    public FileProcessingException(String message, String fileName, Throwable cause) {
        super(String.format("%s. Файл: %s", message, fileName), cause);
    }
}
