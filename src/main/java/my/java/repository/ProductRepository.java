// src/main/java/my/java/repository/ProductRepository.java
package my.java.repository;

import my.java.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Поиск по идентификатору продукта
    Optional<Product> findByProductId(String productId);

    // Поиск по имени продукта и клиенту
    Optional<Product> findByProductNameAndClientId(String productName, Long clientId);

    // Проверка существования продукта по имени для клиента
    boolean existsByProductNameAndClientId(String productName, Long clientId);

    // Проверка существования продукта по внешнему идентификатору
    boolean existsByProductIdAndClientId(String productId, Long clientId);

    // Поиск продуктов по клиенту
    List<Product> findByClientId(Long clientId);

    // Поиск продуктов по источнику данных
    List<Product> findByClientIdAndDataSource(Long clientId, String dataSource);

    // Поиск продуктов по части имени
    List<Product> findByProductNameContainingIgnoreCaseAndClientId(String namePart, Long clientId);

    // Поиск продуктов по категории
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND " +
            "(p.productCategory1 = :category OR p.productCategory2 = :category OR p.productCategory3 = :category)")
    List<Product> findByCategory(@Param("clientId") Long clientId, @Param("category") String category);

    // Поиск продуктов по диапазону цен
    List<Product> findByClientIdAndProductPriceBetween(Long clientId, Double minPrice, Double maxPrice);

    // Удаление продуктов по идентификатору файла
    void deleteByFileIdAndClientId(Long fileId, Long clientId);
}