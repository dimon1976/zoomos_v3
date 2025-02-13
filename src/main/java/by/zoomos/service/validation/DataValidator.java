package by.zoomos.service.validation;

/**
 * Интерфейс для валидации данных
 *
 * @param <T> тип валидируемых данных
 */
public interface DataValidator<T> {

    /**
     * Проверяет данные на соответствие правилам
     *
     * @param data данные для проверки
     * @throws ValidationException если данные не соответствуют правилам
     */
    void validate(T data);

    /**
     * Проверяет, поддерживает ли валидатор данный тип данных
     *
     * @param data данные для проверки
     * @return true если валидатор поддерживает этот тип данных
     */
    boolean supports(Class<?> data);
}