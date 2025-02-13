package by.zoomos.exception;

/**
 * Исключение, выбрасываемое при ошибках экспорта данных
 */
public class ExportException extends RuntimeException {

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExportException(String message, String format) {
        super(String.format("%s. Формат: %s", message, format));
    }

    public ExportException(String message, String format, Throwable cause) {
        super(String.format("%s. Формат: %s", message, format), cause);
    }
}