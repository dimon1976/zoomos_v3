package by.zoomos.service.validation;

import by.zoomos.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис для валидации данных
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final List<DataValidator<?>> validators;

    /**
     * Валидирует данные с помощью подходящего валидатора
     *
     * @param data данные для валидации
     * @throws ValidationException если данные не прошли валидацию
     */
    @SuppressWarnings("unchecked")
    public <T> void validate(T data) {
        log.debug("Валидация данных типа: {}", data.getClass().getSimpleName());

        DataValidator<T> validator = (DataValidator<T>) validators.stream()
                .filter(v -> v.supports(data.getClass()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "Не найден подходящий валидатор для типа: " + data.getClass().getSimpleName()));

        validator.validate(data);
    }

    /**
     * Валидирует список данных
     *
     * @param dataList список данных для валидации
     * @throws ValidationException если данные не прошли валидацию
     */
    public <T> void validateAll(List<T> dataList) {
        log.debug("Валидация списка данных размером: {}", dataList.size());

        for (T data : dataList) {
            validate(data);
        }
    }
}