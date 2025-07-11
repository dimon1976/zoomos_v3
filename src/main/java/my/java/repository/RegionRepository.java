// src/main/java/my/java/repository/RegionRepository.java
package my.java.repository;

import my.java.model.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с региональными данными
 */
@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {

    /**
     * Найти все регионы клиента
     */
    List<Region> findByClientIdOrderByRegion(Long clientId);

    /**
     * Найти регион по названию и клиенту
     */
    Optional<Region> findByClientIdAndRegion(Long clientId, String region);

    /**
     * Найти регионы по части названия
     */
    @Query("SELECT r FROM Region r WHERE r.clientId = :clientId AND " +
            "LOWER(r.region) LIKE LOWER(CONCAT('%', :regionPart, '%'))")
    List<Region> findByClientIdAndRegionContainingIgnoreCase(
            @Param("clientId") Long clientId,
            @Param("regionPart") String regionPart);

    /**
     * Получить количество регионов для клиента
     */
    long countByClientId(Long clientId);

    /**
     * Удалить записи регионов по списку product_id
     */
    void deleteByProductIdIn(List<Long> productIds);

    /**
     * Проверить существование региона
     */
    boolean existsByClientIdAndRegion(Long clientId, String region);

    /**
     * Найти уникальные названия регионов для клиента
     */
    @Query("SELECT DISTINCT r.region FROM Region r WHERE r.clientId = :clientId ORDER BY r.region")
    List<String> findDistinctRegionsByClientId(@Param("clientId") Long clientId);

    /**
     * Найти регионы, созданные после указанной даты
     */
    List<Region> findByClientIdAndCreatedAtAfterOrderByCreatedAtDesc(Long clientId, ZonedDateTime after);

    /**
     * Найти недавно созданные регионы
     */
    @Query("SELECT r FROM Region r WHERE r.clientId = :clientId AND r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<Region> findRecentlyCreated(@Param("clientId") Long clientId, @Param("since") ZonedDateTime since);
}