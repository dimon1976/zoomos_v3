// src/main/java/my/java/service/region/RegionDataService.java
package my.java.service.entity.region;

import my.java.model.entity.Region;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления данными регионов
 */
public interface RegionService {

    /**
     * Сохраняет данные региона
     * @param region данные региона для сохранения
     * @return сохраненные данные региона
     */
    Region saveRegion(Region region);

    /**
     * Сохраняет список данных регионов
     * @param regionList список данных регионов для сохранения
     * @return количество сохраненных записей
     */
    int saveRegionList(List<Region> regionList);

    /**
     * Находит данные региона по ID
     * @param id идентификатор данных региона
     * @return опциональные данные региона
     */
    Optional<Region> findById(Long id);

    /**
     * Находит данные регионов по продукту
     * @param productId идентификатор продукта
     * @return список данных регионов
     */
    List<Region> findByProductId(Long productId);

    /**
     * Находит данные регионов по клиенту
     * @param clientId идентификатор клиента
     * @return список данных регионов
     */
    List<Region> findByClientId(Long clientId);

    /**
     * Удаляет данные региона
     * @param id идентификатор данных региона
     */
    void deleteRegion(Long id);

    /**
     * Удаляет данные регионов по продукту
     * @param productId идентификатор продукта
     * @return количество удаленных записей
     */
    int deleteByProductId(Long productId);

    /**
     * Удаляет данные регионов по клиенту
     * @param clientId идентификатор клиента
     * @return количество удаленных записей
     */
    int deleteByClientId(Long clientId);

    /**
     * Обновляет существующие данные региона или создает новые
     * @param region данные региона
     * @return обновленные или созданные данные региона
     */
    Region upsertRegion(Region region);
}