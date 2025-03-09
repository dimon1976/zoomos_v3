package my.java.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Optional;

/**
 * Глобальный обработчик исключений для всего приложения
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String DEFAULT_ERROR_VIEW = "error/general";
    private static final String FILE_ERROR_VIEW = "error/file-error";
    private static final String ACCESS_DENIED_VIEW = "error/access-denied";
    private static final String NOT_FOUND_VIEW = "error/not-found";

    /**
     * Обработка EntityNotFoundException (объект не найден)
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEntityNotFoundException(EntityNotFoundException ex, Model model, HttpServletRequest request) {
        log.error("Entity not found: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        model.addAttribute("timestamp", System.currentTimeMillis());
        return NOT_FOUND_VIEW;
    }

    /**
     * Обработка ошибок доступа к файлу
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ex, Model model, HttpServletRequest request) {
        log.error("Access denied: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "Доступ запрещен: " + ex.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        model.addAttribute("timestamp", System.currentTimeMillis());
        return ACCESS_DENIED_VIEW;
    }

    /**
     * Обработка ошибок валидации форм
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidationExceptions(Exception ex, Model model, HttpServletRequest request) {
        log.error("Validation error: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "Ошибка валидации данных. Пожалуйста, проверьте введенные данные.");
        model.addAttribute("requestUri", request.getRequestURI());
        model.addAttribute("timestamp", System.currentTimeMillis());

        return DEFAULT_ERROR_VIEW;
    }

    /**
     * Обработка ошибок валидации полей
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleConstraintViolationException(ConstraintViolationException ex, Model model, HttpServletRequest request) {
        log.error("Constraint violation: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "Ошибка валидации данных: " + ex.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        model.addAttribute("timestamp", System.currentTimeMillis());

        return DEFAULT_ERROR_VIEW;
    }

    /**
     * Обработка ошибок размера загружаемого файла
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, Model model, HttpServletRequest request) {
        log.error("File size exceeded: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "Превышен максимальный размер файла. Максимальный размер: 10MB.");
        model.addAttribute("requestUri", request.getRequestURI());
        model.addAttribute("timestamp", System.currentTimeMillis());

        return FILE_ERROR_VIEW;
    }

    /**
     * Обработка ошибок файловых операций
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleFileProcessingError(IOException ex, Model model, HttpServletRequest request) {
        log.error("File processing error: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "Ошибка обработки файла: " + ex.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        model.addAttribute("timestamp", System.currentTimeMillis());

        return FILE_ERROR_VIEW;
    }

    /**
     * Обработка пользовательских исключений файловых операций
     */
    @ExceptionHandler(FileOperationException.class)
    public Object handleFileOperationException(FileOperationException ex, Model model,
                                               HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.error("File operation error: {}", ex.getMessage(), ex);

        // Если указано перенаправление, используем его
        if (ex.getRedirectUrl() != null && !ex.getRedirectUrl().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return new RedirectView(ex.getRedirectUrl(), true);
        }

        // Иначе отображаем страницу ошибки
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        model.addAttribute("timestamp", System.currentTimeMillis());
        return FILE_ERROR_VIEW;
    }

    /**
     * Обработка всех остальных исключений
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model, HttpServletRequest request) {
        // Извлекаем реальное исключение из запроса, если оно есть
        Throwable rootCause = Optional.ofNullable((Throwable) request.getAttribute("javax.servlet.error.exception"))
                .orElse(ex);

        log.error("Unhandled exception: {}", rootCause.getMessage(), rootCause);
        model.addAttribute("errorMessage", "Произошла непредвиденная ошибка: " + rootCause.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        model.addAttribute("timestamp", System.currentTimeMillis());

        return DEFAULT_ERROR_VIEW;
    }
}