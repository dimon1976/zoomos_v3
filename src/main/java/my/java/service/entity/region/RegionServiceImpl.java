// src/main/java/my/java/service/region/RegionDataServiceImpl.java
package my.java.service.entity.region;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Region;
import my.java.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private final RegionRepository regionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Region> findByRegionAndProductId(String regionName, Long productId) {
        return regionRepository.findByRegionAndProductId(regionName, productId);
    }

    @Override
    @Transactional
    public Region saveRegion(Region region) {
        log.debug("Сохранение данных региона: {}", region.getRegion());
        return regionRepository.save(region);
    }

    @Override
    @Transactional
    public int saveRegionList(List<Region> regionList) {
        if (regionList == null || regionList.isEmpty()) {
            return 0;
        }

        log.debug("Сохранение {} записей данных регионов", regionList.size());
        List<Region> savedRegionData = regionRepository.saveAll(regionList);
        return savedRegionData.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Region> findById(Long id) {
        log.debug("Поиск данных региона по ID: {}", id);
        return regionRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Region> findByProductId(Long productId) {
        log.debug("Поиск данных регионов по productId: {}", productId);
        return regionRepository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Region> findByClientId(Long clientId) {
        log.debug("Поиск данных регионов по clientId: {}", clientId);
        return regionRepository.findByClientId(clientId);
    }

    @Override
    @Transactional
    public void deleteRegion(Long id) {
        log.debug("Удаление данных региона по ID: {}", id);
        regionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        log.debug("Удаление данных регионов по productId: {}", productId);

        // Сначала подсчитываем количество записей для удаления
        List<Region> recordsToDelete = regionRepository.findByProductId(productId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        regionRepository.deleteByProductId(productId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        log.debug("Удаление данных регионов по clientId: {}", clientId);

        // Сначала подсчитываем количество записей для удаления
        List<Region> recordsToDelete = regionRepository.findByClientId(clientId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        regionRepository.deleteByClientId(clientId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public Region upsertRegion(Region region) {
        log.debug("Обновление/создание данных региона: {}", region.getRegion());

        // Проверяем существование данных региона по региону и продукту
        if (region.getRegion() != null && region.getProduct() != null) {
            Optional<Region> existingData = regionRepository.findByRegionAndProductId(
                    region.getRegion(), region.getProduct().getId());

            if (existingData.isPresent()) {
                // Обновляем существующие данные
                Region existing = existingData.get();
                // Обновляем все поля, кроме ID и продукта
                copyRegionDataFields(region, existing);
                return regionRepository.save(existing);
            }
        }

        // Создаем новые данные региона
        return regionRepository.save(region);
    }

    /**
     * Копирует поля из исходных данных региона в целевые данные региона
     * @param source исходные данные региона
     * @param target целевые данные региона
     */
    private void copyRegionDataFields(Region source, Region target) {
        target.setRegion(source.getRegion());
        target.setRegionAddress(source.getRegionAddress());
        target.setClientId(source.getClientId());

        // Не копируем связанные сущности и ID
    }
}