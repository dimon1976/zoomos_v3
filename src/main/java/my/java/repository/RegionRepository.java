// src/main/java/my/java/repository/RegionDataRepository.java
package my.java.repository;

import my.java.model.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    // Поиск данных региона по продукту
    List<Region> findByProductId(Long productId);

    // Поиск данных региона по клиенту
    List<Region> findByClientId(Long clientId);

    // Поиск данных региона по названию региона и продукту
    Optional<Region> findByRegionAndProductId(String region, Long productId);

    // Проверка существования данных региона
    boolean existsByRegionAndProductId(String region, Long productId);

    // Поиск по части названия региона
    List<Region> findByRegionContainingIgnoreCaseAndClientId(String regionPart, Long clientId);

    // Удаление данных региона по продукту
    void deleteByProductId(Long productId);

    // Удаление данных региона по клиенту
    void deleteByClientId(Long clientId);

    // Поиск уникальных регионов для клиента
    List<String> findDistinctRegionByClientId(Long clientId);
}