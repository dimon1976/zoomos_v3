package my.java.model.entity;

import my.java.util.transformer.ValueTransformerFactory;

import java.util.Map;

/**
 * Интерфейс для сущностей, которые могут быть импортированы из файлов.
 * Вместо использования аннотаций для сопоставления полей, каждая сущность
 * предоставляет метод для заполнения своих полей из Map с данными.
 */
public interface ImportableEntity {

    /**
     * Заполняет поля сущности из карты с данными.
     *
     * @param data карта, где ключ - имя поля (или заголовок файла), значение - строковое значение
     * @return true, если заполнение прошло успешно
     */
    boolean fillFromMap(Map<String, String> data);

    /**
     * Возвращает карту соответствия заголовков файла и полей сущности.
     *
     * @return карта, где ключ - заголовок файла, значение - имя поля в сущности
     */
    Map<String, String> getFieldMappings();

    /**
     * Валидирует заполненную сущность.
     *
     * @return null, если валидация прошла успешно, иначе - сообщение об ошибке
     */
    String validate();

    /**
     * Устанавливает фабрику трансформеров для преобразования строковых значений.
     *
     * @param transformerFactory фабрика трансформеров
     */
    void setTransformerFactory(ValueTransformerFactory transformerFactory);


    /**
     * Устанавливает ID клиента
     *
     * @param clientId ID клиента
     */
    void setClientId(Long clientId);
}