package my.java.service.market;

import my.java.model.entity.MarketData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Универсальный сервис для управления рыночными данными (регионы и конкуренты).
 * Заменяет отдельные сервисы RegionDataService и CompetitorDataService.
 */
public interface MarketDataService {

    /**
     * Сохраняет рыночные данные
     * @param marketData данные для сохранения
     * @return сохраненные данные
     */
    MarketData saveMarketData(MarketData marketData);

    /**
     * Сохраняет список рыночных данных
     * @param marketDataList список данных для сохранения
     * @return количество сохраненных записей
     */
    int saveMarketDataList(List<MarketData> marketDataList);

    /**
     * Находит рыночные данные по ID
     * @param id идентификатор данных
     * @return опциональные данные
     */
    Optional<MarketData> findById(Long id);

    /**
     * Находит рыночные данные по продукту
     * @param productId идентификатор продукта
     * @return список данных
     */
    List<MarketData> findByProductId(Long productId);

    /**
     * Находит рыночные данные по клиенту
     * @param clientId идентификатор клиента
     * @return список данных
     */
    List<MarketData> findByClientId(Long clientId);

    /**
     * Находит рыночные данные по дате
     * @param date дата отсечки
     * @param clientId идентификатор клиента
     * @return список данных
     */
    List<MarketData> findByDateAfterAndClientId(LocalDateTime date, Long clientId);

    /**
     * Находит рыночные данные по региону
     * @param region название региона
     * @return список данных
     */
    List<MarketData> findByRegion(String region);

    /**
     * Находит рыночные данные по конкуренту
     * @param competitorName название конкурента
     * @return список данных
     */
    List<MarketData> findByCompetitorName(String competitorName);

    /**
     * Удаляет рыночные данные
     * @param id идентификатор данных
     */
    void deleteMarketData(Long id);

    /**
     * Удаляет рыночные данные по продукту
     * @param productId идентификатор продукта
     * @return количество удаленных записей
     */
    int deleteByProductId(Long productId);

    /**
     * Удаляет рыночные данные по клиенту
     * @param clientId идентификатор клиента
     * @return количество удаленных записей
     */
    int deleteByClientId(Long clientId);

    /**
     * Обновляет существующие рыночные данные или создает новые
     * @param marketData данные для обновления/создания
     * @return обновленные или созданные данные
     */
    MarketData upsertMarketData(MarketData marketData);

    /**
     * Возвращает список всех уникальных регионов
     * @return множество названий регионов
     */
    Set<String> findAllRegions();

    /**
     * Возвращает список всех уникальных конкурентов
     * @return множество названий конкурентов
     */
    Set<String> findAllCompetitors();

    /**
     * Возвращает список всех уникальных регионов для заданного продукта
     * @param productId идентификатор продукта
     * @return множество названий регионов
     */
    Set<String> findRegionsByProductId(Long productId);

    /**
     * Возвращает список всех уникальных конкурентов для заданного продукта
     * @param productId идентификатор продукта
     * @return множество названий конкурентов
     */
    Set<String> findCompetitorsByProductId(Long productId);
}