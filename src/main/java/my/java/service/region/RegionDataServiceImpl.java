// src/main/java/my/java/service/region/RegionDataServiceImpl.java
package my.java.service.region;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.RegionData;
import my.java.repository.RegionDataRepository;
import my.java.service.base.BaseEntityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Реализация сервиса для управления данными регионов
 */
@Service
@Slf4j
public class RegionDataServiceImpl extends BaseEntityService<RegionData, Long, RegionDataRepository> implements RegionDataService {

    public RegionDataServiceImpl(RegionDataRepository repository) {
        super(repository);
    }

    @Override
    protected void logSave(RegionData entity) {
        log.debug("Сохранение данных региона: {}", entity.getRegion());
    }

    @Override
    public RegionData saveRegionData(RegionData regionData) {
        return save(regionData);
    }

    @Override
    public int saveRegionDataList(List<RegionData> regionDataList) {
        return saveAll(regionDataList);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionData> findByProductId(Long productId) {
        log.debug("Поиск данных регионов по productId: {}", productId);
        return repository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionData> findByClientId(Long clientId) {
        log.debug("Поиск данных регионов по clientId: {}", clientId);
        return repository.findByClientId(clientId);
    }

    @Override
    public void deleteRegionData(Long id) {
        deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        log.debug("Удаление данных регионов по productId: {}", productId);
        return deleteEntitiesByCondition(() -> repository.findByProductId(productId),
                () -> repository.deleteByProductId(productId));
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        log.debug("Удаление данных регионов по clientId: {}", clientId);
        return deleteEntitiesByCondition(() -> repository.findByClientId(clientId),
                () -> repository.deleteByClientId(clientId));
    }

    /**
     * Удаляет сущности по условию
     *
     * @param findEntities поставщик списка сущностей для удаления
     * @param deleteOperation операция удаления
     * @return количество удаленных сущностей
     */
    private int deleteEntitiesByCondition(Supplier<List<RegionData>> findEntities,
                                          Runnable deleteOperation) {
        List<RegionData> recordsToDelete = findEntities.get();
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        deleteOperation.run();
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public RegionData upsertRegionData(RegionData regionData) {
        log.debug("Обновление/создание данных региона: {}", regionData.getRegion());

        if (canFindExistingEntity(regionData)) {
            Optional<RegionData> existingData = repository.findByRegionAndProductId(
                    regionData.getRegion(), regionData.getProduct().getId());

            if (existingData.isPresent()) {
                RegionData existing = existingData.get();
                copyRegionDataFields(regionData, existing);
                return repository.save(existing);
            }
        }

        return repository.save(regionData);
    }

    /**
     * Проверяет возможность поиска существующей сущности
     *
     * @param regionData данные региона
     * @return true, если возможно найти существующую сущность
     */
    private boolean canFindExistingEntity(RegionData regionData) {
        return regionData.getRegion() != null &&
                regionData.getProduct() != null;
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