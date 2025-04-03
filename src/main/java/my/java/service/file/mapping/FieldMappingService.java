package my.java.service.file.mapping;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для сервиса маппинга полей при импорте файлов.
 */
public interface FieldMappingService {

    /**
     * Получает маппинг полей по его ID.
     *
     * @param mappingId ID маппинга
     * @return маппинг полей или пустая карта, если маппинг не найден
     */
    Map<String, String> getMappingById(Long mappingId);

    /**
     * Получает список всех доступных маппингов для клиента и типа сущности.
     *
     * @param clientId ID клиента
     * @param entityType тип сущности
     * @return список маппингов
     */
    List<Map<String, Object>> getAvailableMappingsForClient(Long clientId, String entityType);

    /**
     * Создает новый маппинг полей.
     *
     * @param name название маппинга
     * @param description описание маппинга
     * @param clientId ID клиента
     * @param entityType тип сущности
     * @param fieldMapping карта маппинга полей
     * @return ID созданного маппинга
     */
    Long createMapping(String name, String description, Long clientId, String entityType, Map<String, String> fieldMapping);

    /**
     * Обновляет существующий маппинг полей.
     *
     * @param mappingId ID маппинга
     * @param name новое название
     * @param description новое описание
     * @param fieldMapping новая карта маппинга полей
     * @return true, если обновление успешно
     */
    boolean updateMapping(Long mappingId, String name, String description, Map<String, String> fieldMapping);

    /**
     * Удаляет маппинг полей.
     *
     * @param mappingId ID маппинга
     * @return true, если удаление успешно
     */
    boolean deleteMapping(Long mappingId);

    /**
     * Сопоставляет заголовки из файла с полями сущности.
     *
     * @param headers заголовки из файла
     * @param entityType тип сущности
     * @return предложенное сопоставление полей
     */
    Map<String, String> suggestMapping(List<String> headers, String entityType);

    /**
     * Получает метаданные о полях для указанного типа сущности.
     *
     * @param entityType тип сущности
     * @return метаданные о полях
     */
    Map<String, Object> getEntityFieldsMetadata(String entityType);
}