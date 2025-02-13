package by.zoomos.exception;

/**
 * Исключение, выбрасываемое при ошибках маппинга данных
 */
public class MappingException extends RuntimeException {

    public MappingException(String message) {
        super(message);
    }

    public MappingException(String message, Throwable cause) {
        super(message, cause);
    }

    public MappingException(String message, String columnName) {
        super(String.format("%s. Колонка: %s", message, columnName));
    }

    public MappingException(String message, String columnName, Throwable cause) {
        super(String.format("%s. Колонка: %s", message, columnName), cause);
    }
}
