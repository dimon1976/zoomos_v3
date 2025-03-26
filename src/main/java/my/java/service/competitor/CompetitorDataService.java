// src/main/java/my/java/service/competitor/CompetitorDataService.java
package my.java.service.competitor;

import my.java.model.entity.CompetitorData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления данными конкурентов
 */
public interface CompetitorDataService {

    /**
     * Сохраняет данные конкурента
     * @param competitorData данные конкурента для сохранения
     * @return сохраненные данные конкурента
     */
    CompetitorData saveCompetitorData(CompetitorData competitorData);

    /**
     * Сохраняет список данных конкурентов
     * @param competitorDataList список данных конкурентов для сохранения
     * @return количество сохраненных записей
     */
    int saveCompetitorDataList(List<CompetitorData> competitorDataList);

    /**
     * Находит данные конкурента по ID
     * @param id идентификатор данных конкурента
     * @return опциональные данные конкурента
     */
    Optional<CompetitorData> findById(Long id);

    /**
     * Находит данные конкурентов по продукту
     * @param productId идентификатор продукта
     * @return список данных конкурентов
     */
    List<CompetitorData> findByProductId(Long productId);

    /**
     * Находит данные конкурентов по клиенту
     * @param clientId идентификатор клиента
     * @return список данных конкурентов
     */
    List<CompetitorData> findByClientId(Long clientId);

    /**
     * Находит данные конкурентов по дате
     * @param date дата отсечки
     * @param clientId идентификатор клиента
     * @return список данных конкурентов
     */
    List<CompetitorData> findByDateAfterAndClientId(LocalDateTime date, Long clientId);

    /**
     * Удаляет данные конкурента
     * @param id идентификатор данных конкурента
     */
    void deleteCompetitorData(Long id);

    /**
     * Удаляет данные конкурентов по продукту
     * @param productId идентификатор продукта
     * @return количество удаленных записей
     */
    int deleteByProductId(Long productId);

    /**
     * Удаляет данные конкурентов по клиенту
     * @param clientId идентификатор клиента
     * @return количество удаленных записей
     */
    int deleteByClientId(Long clientId);

    /**
     * Обновляет существующие данные конкурента или создает новые
     * @param competitorData данные конкурента
     * @return обновленные или созданные данные конкурента
     */
    CompetitorData upsertCompetitorData(CompetitorData competitorData);

    List<CompetitorData> findByIds(List<Long> ids);
}