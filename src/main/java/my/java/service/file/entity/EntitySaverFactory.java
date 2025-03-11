// src/main/java/my/java/service/file/entity/EntitySaverFactory.java
package my.java.service.file.entity;

import my.java.model.entity.ImportableEntity;

import java.util.List;
import java.util.function.Function;

/**
 * Фабрика для получения компонентов, сохраняющих различные типы сущностей
 */
public interface EntitySaverFactory {

    /**
     * Получает функцию для сохранения сущностей определенного типа
     *
     * @param entityType тип сущности
     * @return функция для сохранения списка сущностей
     */
    Function<List<ImportableEntity>, Integer> getSaver(String entityType);

    /**
     * Регистрирует новую функцию для сохранения сущностей
     *
     * @param entityType тип сущности
     * @param saverFunction функция для сохранения сущностей
     */
    void registerSaver(String entityType, Function<List<ImportableEntity>, Integer> saverFunction);

    /**
     * Проверяет, зарегистрирован ли обработчик для указанного типа сущности
     *
     * @param entityType тип сущности
     * @return true, если обработчик зарегистрирован
     */
    boolean hasSaver(String entityType);
}