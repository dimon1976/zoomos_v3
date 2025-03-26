// src/main/java/my/java/service/region/RegionDataService.java
package my.java.service.region;

import my.java.model.entity.RegionData;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления данными регионов
 */
public interface RegionDataService {

    /**
     * Сохраняет данные региона
     * @param regionData данные региона для сохранения
     * @return сохраненные данные региона
     */
    RegionData saveRegionData(RegionData regionData);

    /**
     * Сохраняет список данных регионов
     * @param regionDataList список данных регионов для сохранения
     * @return количество сохраненных записей
     */
    int saveRegionDataList(List<RegionData> regionDataList);

    /**
     * Находит данные региона по ID
     * @param id идентификатор данных региона
     * @return опциональные данные региона
     */
    Optional<RegionData> findById(Long id);

    /**
     * Находит данные регионов по продукту
     * @param productId идентификатор продукта
     * @return список данных регионов
     */
    List<RegionData> findByProductId(Long productId);

    /**
     * Находит данные регионов по клиенту
     * @param clientId идентификатор клиента
     * @return список данных регионов
     */
    List<RegionData> findByClientId(Long clientId);

    /**
     * Удаляет данные региона
     * @param id идентификатор данных региона
     */
    void deleteRegionData(Long id);

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
     * @param regionData данные региона
     * @return обновленные или созданные данные региона
     */
    RegionData upsertRegionData(RegionData regionData);

    List<RegionData> findByIds(List<Long> ids);
}