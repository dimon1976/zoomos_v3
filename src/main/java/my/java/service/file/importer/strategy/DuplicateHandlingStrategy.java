// src/main/java/my/java/service/file/importer/strategy/DuplicateHandlingStrategy.java
package my.java.service.file.importer.strategy;

import my.java.model.entity.ImportableEntity;
import my.java.service.file.importer.BatchSaveResult;
import my.java.service.file.importer.EntityRelationshipHolder;
import java.util.List;

/**
 * Упрощенный интерфейс для стратегий обработки дубликатов
 * Дубликаты определяются внутри загружаемого файла по productId
 */
public interface DuplicateHandlingStrategy {

    /**
     * Обработка данных с учетом дубликатов внутри файла
     *
     * @param holder контейнер с данными из файла
     * @param clientId ID клиента
     * @return результат обработки
     */
    BatchSaveResult process(EntityRelationshipHolder holder, Long clientId);

    /**
     * Тип стратегии
     */
    String getStrategyType();
}