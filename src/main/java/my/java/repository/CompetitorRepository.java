// src/main/java/my/java/repository/CompetitorDataRepository.java
package my.java.repository;

import my.java.model.entity.Competitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompetitorRepository extends JpaRepository<Competitor, Long> {

    // Поиск данных конкурентов по продукту
    List<Competitor> findByProductId(Long productId);

    // Поиск данных конкурентов по клиенту
    List<Competitor> findByClientId(Long clientId);

    // Поиск данных конкурента по названию и продукту
    Optional<Competitor> findByCompetitorNameAndProductId(String competitorName, Long productId);

    // Поиск данных конкурентов по дате
    List<Competitor> findByCompetitorLocalDateTimeAfterAndClientId(
            LocalDateTime date, Long clientId);

    // Поиск данных по клиенту, отсортированных по дате
    List<Competitor> findByClientIdOrderByCompetitorLocalDateTimeDesc(Long clientId);

    // Поиск по URL конкурента
    List<Competitor> findByCompetitorUrlContainingAndClientId(String urlPart, Long clientId);

    // Поиск по диапазону цен конкурентов
    @Query("SELECT c FROM Competitor c WHERE c.clientId = :clientId AND " +
            "CAST(REPLACE(c.competitorPrice, ',', '.') AS double) BETWEEN :minPrice AND :maxPrice")
    List<Competitor> findByPriceRange(
            @Param("clientId") Long clientId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice);

    // Удаление данных конкурентов по продукту
    void deleteByProductId(Long productId);

    // Удаление данных конкурентов по клиенту
    void deleteByClientId(Long clientId);

    // Получение списка уникальных названий конкурентов для клиента
    @Query("SELECT DISTINCT c.competitorName FROM Competitor c WHERE c.clientId = :clientId")
    List<String> findDistinctCompetitorNamesByClientId(@Param("clientId") Long clientId);
}