package by.zoomos.repository;

import by.zoomos.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.QueryHint;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Репозиторий для работы с сущностью Product
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Находит продукт по productId со всеми связанными данными
     *
     * @param productId идентификатор продукта
     * @return Optional с найденным продуктом
     */
    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.regionData rd
            LEFT JOIN FETCH p.competitorData cd
            WHERE p.productId = :productId
            """)
    Optional<Product> findByProductIdWithAllData(@Param("productId") String productId);

    /**
     * Находит продукт по productId
     *
     * @param productId идентификатор продукта
     * @return Optional с найденным продуктом
     */
    Optional<Product> findByProductId(String productId);

    /**
     * Получает поток продуктов по clientId со всеми связанными данными
     *
     * @param clientId идентификатор клиента
     * @return поток продуктов
     */
    @QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.FETCH_SIZE, value = "1000"))
    @Query("""
            SELECT DISTINCT p FROM Product p
            LEFT JOIN FETCH p.regionData rd
            LEFT JOIN FETCH p.competitorData cd
            WHERE p.clientId = :clientId
            """)
    Stream<Product> streamByClientIdWithAllData(@Param("clientId") Long clientId);
}