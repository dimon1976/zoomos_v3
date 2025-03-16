package my.java.repository;

import my.java.model.entity.MarketData;
import my.java.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Репозиторий для работы с объединенной сущностью MarketData.
 * Заменяет отдельные репозитории для RegionData и CompetitorData.
 */
@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    /**
     * Находит все рыночные данные по ID продукта.
     */
    List<MarketData> findByProductId(Long productId);

    /**
     * Находит все рыночные данные по ID клиента.
     */
    List<MarketData> findByClientId(Long clientId);

    /**
     * Находит все рыночные данные для указанного продукта.
     */
    List<MarketData> findByProduct(Product product);

    /**
     * Находит все рыночные данные по региону.
     */
    List<MarketData> findByRegion(String region);

    /**
     * Находит все рыночные данные по конкуренту.
     */
    List<MarketData> findByCompetitorName(String competitorName);

    /**
     * Находит все рыночные данные по региону и ID продукта.
     */
    Optional<List<MarketData>> findByRegionAndProductId(String region, Long productId);

    /**
     * Находит все рыночные данные по конкуренту и ID продукта.
     */
    Optional<List<MarketData>> findByCompetitorNameAndProductId(String competitorName, Long productId);

    /**
     * Возвращает список всех уникальных регионов.
     */
    @Query("SELECT DISTINCT m.region FROM MarketData m WHERE m.region IS NOT NULL")
    Set<String> findAllRegions();

    /**
     * Возвращает список всех уникальных конкурентов.
     */
    @Query("SELECT DISTINCT m.competitorName FROM MarketData m WHERE m.competitorName IS NOT NULL")
    Set<String> findAllCompetitors();

    /**
     * Возвращает список всех уникальных регионов для заданного продукта.
     */
    @Query("SELECT DISTINCT m.region FROM MarketData m WHERE m.product.id = :productId AND m.region IS NOT NULL")
    Set<String> findRegionsByProductId(@Param("productId") Long productId);

    /**
     * Возвращает список всех уникальных конкурентов для заданного продукта.
     */
    @Query("SELECT DISTINCT m.competitorName FROM MarketData m WHERE m.product.id = :productId AND m.competitorName IS NOT NULL")
    Set<String> findCompetitorsByProductId(@Param("productId") Long productId);
}