package by.zoomos.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * Исключение, выбрасываемое при ошибках валидации
 */
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = new ArrayList<>();
        this.errors.add(message);
    }

    public ValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.errors = new ArrayList<>();
        this.errors.add(message);
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    @Override
    public String getMessage() {
        return String.join("; ", errors);
    }
}