// src/main/java/my/java/service/entity/region/RegionServiceImpl.java
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
        return regionRepository.save(region);
    }

    @Override
    @Transactional
    public int saveRegionList(List<Region> regionList) {
        if (regionList == null || regionList.isEmpty()) return 0;
        return regionRepository.saveAll(regionList).size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Region> findById(Long id) {
        return regionRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Region> findByProductId(Long productId) {
        return regionRepository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Region> findByClientId(Long clientId) {
        return regionRepository.findByClientId(clientId);
    }

    @Override
    @Transactional
    public void deleteRegion(Long id) {
        regionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        List<Region> recordsToDelete = regionRepository.findByProductId(productId);
        if (recordsToDelete.isEmpty()) return 0;

        regionRepository.deleteByProductId(productId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        List<Region> recordsToDelete = regionRepository.findByClientId(clientId);
        if (recordsToDelete.isEmpty()) return 0;

        regionRepository.deleteByClientId(clientId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public Region upsertRegion(Region region) {
        if (region.getRegion() != null && region.getProduct() != null) {
            Optional<Region> existingData = regionRepository.findByRegionAndProductId(
                    region.getRegion(), region.getProduct().getId());

            if (existingData.isPresent()) {
                Region existing = existingData.get();
                copyRegionDataFields(region, existing);
                return regionRepository.save(existing);
            }
        }
        return regionRepository.save(region);
    }

    private void copyRegionDataFields(Region source, Region target) {
        target.setRegion(source.getRegion());
        target.setRegionAddress(source.getRegionAddress());
        target.setClientId(source.getClientId());
    }
}