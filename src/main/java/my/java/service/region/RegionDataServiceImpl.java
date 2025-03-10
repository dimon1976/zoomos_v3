// src/main/java/my/java/service/region/RegionDataServiceImpl.java
package my.java.service.region;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.RegionData;
import my.java.repository.RegionDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegionDataServiceImpl implements RegionDataService {

    private final RegionDataRepository regionDataRepository;

    @Override
    @Transactional
    public RegionData saveRegionData(RegionData regionData) {
        log.debug("Сохранение данных региона: {}", regionData.getRegion());
        return regionDataRepository.save(regionData);
    }

    @Override
    @Transactional
    public int saveRegionDataList(List<RegionData> regionDataList) {
        if (regionDataList == null || regionDataList.isEmpty()) {
            return 0;
        }

        log.debug("Сохранение {} записей данных регионов", regionDataList.size());
        List<RegionData> savedRegionData = regionDataRepository.saveAll(regionDataList);
        return savedRegionData.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegionData> findById(Long id) {
        log.debug("Поиск данных региона по ID: {}", id);
        return regionDataRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionData> findByProductId(Long productId) {
        log.debug("Поиск данных регионов по productId: {}", productId);
        return regionDataRepository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionData> findByClientId(Long clientId) {
        log.debug("Поиск данных регионов по clientId: {}", clientId);
        return regionDataRepository.findByClientId(clientId);
    }

    @Override
    @Transactional
    public void deleteRegionData(Long id) {
        log.debug("Удаление данных региона по ID: {}", id);
        regionDataRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        log.debug("Удаление данных регионов по productId: {}", productId);

        // Сначала подсчитываем количество записей для удаления
        List<RegionData> recordsToDelete = regionDataRepository.findByProductId(productId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        regionDataRepository.deleteByProductId(productId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        log.debug("Удаление данных регионов по clientId: {}", clientId);

        // Сначала подсчитываем количество записей для удаления
        List<RegionData> recordsToDelete = regionDataRepository.findByClientId(clientId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        regionDataRepository.deleteByClientId(clientId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public RegionData upsertRegionData(RegionData regionData) {
        log.debug("Обновление/создание данных региона: {}", regionData.getRegion());

        // Проверяем существование данных региона по региону и продукту
        if (regionData.getRegion() != null && regionData.getProduct() != null) {
            Optional<RegionData> existingData = regionDataRepository.findByRegionAndProductId(
                    regionData.getRegion(), regionData.getProduct().getId());

            if (existingData.isPresent()) {
                // Обновляем существующие данные
                RegionData existing = existingData.get();
                // Обновляем все поля, кроме ID и продукта
                copyRegionDataFields(regionData, existing);
                return regionDataRepository.save(existing);
            }
        }

        // Создаем новые данные региона
        return regionDataRepository.save(regionData);
    }

    /**
     * Копирует поля из исходных данных региона в целевые данные региона
     * @param source исходные данные региона
     * @param target целевые данные региона
     */
    private void copyRegionDataFields(RegionData source, RegionData target) {
        target.setRegion(source.getRegion());
        target.setRegionAddress(source.getRegionAddress());
        target.setClientId(source.getClientId());

        // Не копируем связанные сущности и ID
    }
}