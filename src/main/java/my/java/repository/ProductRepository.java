// src/main/java/my/java/repository/ProductRepository.java
package my.java.repository;

import my.java.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с продуктами
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Найти продукт по ID продукта и клиенту
     */
    Optional<Product> findByProductIdAndClientId(String productId, Long clientId);

    /**
     * Найти все продукты клиента
     */
    List<Product> findByClientIdOrderByProductName(Long clientId);

    /**
     * Найти продукты по части названия
     */
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND " +
            "LOWER(p.productName) LIKE LOWER(CONCAT('%', :namePart, '%'))")
    List<Product> findByClientIdAndProductNameContainingIgnoreCase(
            @Param("clientId") Long clientId,
            @Param("namePart") String namePart);

    /**
     * Проверить существование продукта по ID продукта и клиенту
     */
    boolean existsByProductIdAndClientId(String productId, Long clientId);

    /**
     * Получить количество продуктов для клиента
     */
    long countByClientId(Long clientId);

    /**
     * Найти продукты по бренду
     */
    List<Product> findByClientIdAndProductBrandOrderByProductName(Long clientId, String brand);

    /**
     * Найти продукты по категории
     */
    List<Product> findByClientIdAndProductCategory1OrderByProductName(Long clientId, String category);

    /**
     * Пакетное получение существующих ID продуктов
     */
    @Query("SELECT p.productId FROM Product p WHERE p.clientId = :clientId AND p.productId IN :productIds")
    List<String> findExistingProductIds(@Param("clientId") Long clientId, @Param("productIds") List<String> productIds);

    /**
     * Найти продукты, созданные после указанной даты
     */
    List<Product> findByClientIdAndCreatedAtAfterOrderByCreatedAtDesc(Long clientId, ZonedDateTime after);

    /**
     * Найти продукты, обновленные после указанной даты
     */
    List<Product> findByClientIdAndUpdatedAtAfterOrderByUpdatedAtDesc(Long clientId, ZonedDateTime after);

    /**
     * Найти недавно созданные продукты (за последние N часов)
     */
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<Product> findRecentlyCreated(@Param("clientId") Long clientId, @Param("since") ZonedDateTime since);

}