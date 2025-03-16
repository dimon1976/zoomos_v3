package my.java.service.file.builder;

import my.java.model.entity.ImportableEntity;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс для построения набора связанных сущностей из данных одной строки файла.
 */
public interface EntitySetBuilder {

    /**
     * Применяет данные из строки файла к строителю.
     *
     * @param row карта с данными из строки файла
     * @return true, если данные успешно применены
     */
    boolean applyRow(Map<String, String> row);

    /**
     * Устанавливает ID клиента для всех сущностей.
     *
     * @param clientId ID клиента
     * @return this для цепочки вызовов
     */
    EntitySetBuilder withClientId(Long clientId);

    /**
     * Устанавливает ID файла для всех сущностей.
     *
     * @param fileId ID файла
     * @return this для цепочки вызовов
     */
    EntitySetBuilder withFileId(Long fileId);

    /**
     * Строит набор связанных сущностей.
     *
     * @return список всех созданных сущностей
     */
    List<ImportableEntity> build();

    /**
     * Проверяет, является ли строитель валидным.
     *
     * @return null, если строитель валиден, иначе - сообщение об ошибке
     */
    String validate();

    /**
     * Сбрасывает состояние строителя для повторного использования.
     */
    void reset();
}