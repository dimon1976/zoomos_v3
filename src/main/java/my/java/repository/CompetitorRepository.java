// src/main/java/my/java/repository/CompetitorRepository.java
package my.java.repository;

import my.java.model.entity.Competitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Репозиторий для работы с данными конкурентов
 */
@Repository
public interface CompetitorRepository extends JpaRepository<Competitor, Long> {

    /**
     * Найти всех конкурентов клиента
     */
    List<Competitor> findByClientIdOrderByCompetitorLocalDateTimeDesc(Long clientId);

    /**
     * Найти конкурентов по названию сайта
     */
    List<Competitor> findByClientIdAndCompetitorNameOrderByCompetitorLocalDateTimeDesc(
            Long clientId, String competitorName);

    /**
     * Найти конкурентов по названию товара
     */
    @Query("SELECT c FROM Competitor c WHERE c.clientId = :clientId AND " +
            "LOWER(c.competitorProductName) LIKE LOWER(CONCAT('%', :productName, '%')) " +
            "ORDER BY c.competitorLocalDateTime DESC")
    List<Competitor> findByClientIdAndProductNameContaining(
            @Param("clientId") Long clientId,
            @Param("productName") String productName);

    /**
     * Получить количество записей конкурентов для клиента
     */
    long countByClientId(Long clientId);

    /**
     * Удалить записи конкурентов по списку product_id
     */
    void deleteByProductIdIn(List<Long> productIds);

    /**
     * Найти конкурентов за определенный период
     */
    @Query("SELECT c FROM Competitor c WHERE c.clientId = :clientId AND " +
            "c.competitorLocalDateTime BETWEEN :startDate AND :endDate " +
            "ORDER BY c.competitorLocalDateTime DESC")
    List<Competitor> findByClientIdAndDateRange(
            @Param("clientId") Long clientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Найти последние записи конкурентов
     */
    @Query("SELECT c FROM Competitor c WHERE c.clientId = :clientId " +
            "ORDER BY c.competitorLocalDateTime DESC")
    List<Competitor> findLatestByClientId(@Param("clientId") Long clientId);

    /**
     * Удалить старые записи конкурентов (старше указанной даты)
     */
    void deleteByClientIdAndCompetitorLocalDateTimeBefore(Long clientId, LocalDateTime cutoffDate);

    /**
     * Найти конкурентов, созданных после указанной даты
     */
    List<Competitor> findByClientIdAndCreatedAtAfterOrderByCreatedAtDesc(Long clientId, ZonedDateTime after);

    /**
     * Найти недавно созданных конкурентов
     */
    @Query("SELECT c FROM Competitor c WHERE c.clientId = :clientId AND c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<Competitor> findRecentlyCreated(@Param("clientId") Long clientId, @Param("since") ZonedDateTime since);

}