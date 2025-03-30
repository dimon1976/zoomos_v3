// src/main/java/my/java/service/product/ProductService.java
package my.java.service.entity.product;

import my.java.model.entity.Product;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления продуктами
 */
public interface ProductService {

    /**
     * Сохраняет продукт в базе данных
     * @param product продукт для сохранения
     * @return сохраненный продукт
     */
    Product saveProduct(Product product);

    /**
     * Сохраняет список продуктов в базе данных
     * @param products список продуктов для сохранения
     * @return количество сохраненных продуктов
     */
    int saveProducts(List<Product> products);

    /**
     * Находит продукт по ID
     * @param id идентификатор продукта
     * @return опциональный продукт
     */
    Optional<Product> findById(Long id);

    /**
     * Находит продукт по внешнему идентификатору и клиенту
     * @param productId внешний идентификатор продукта
     * @param clientId идентификатор клиента
     * @return опциональный продукт
     */
    Optional<Product> findByProductIdAndClientId(String productId, Long clientId);

    /**
     * Находит продукты по клиенту
     * @param clientId идентификатор клиента
     * @return список продуктов
     */
    List<Product> findByClientId(Long clientId);

    /**
     * Находит продукты по клиенту и источнику данных
     * @param clientId идентификатор клиента
     * @param dataSource источник данных
     * @return список продуктов
     */
    List<Product> findByClientIdAndDataSource(Long clientId, String dataSource);

    /**
     * Удаляет продукт
     * @param id идентификатор продукта
     */
    void deleteProduct(Long id);


    List<Product> findByIds(List<Long> ids);

    /**
     * Удаляет продукты по идентификатору файла и клиенту
     * @param fileId идентификатор файла
     * @param clientId идентификатор клиента
     * @return количество удаленных продуктов
     */
    int deleteByFileIdAndClientId(Long fileId, Long clientId);

    /**
     * Обновляет существующий продукт или создает новый
     * @param product данные продукта
     * @return обновленный или созданный продукт
     */
    Product upsertProduct(Product product);
}