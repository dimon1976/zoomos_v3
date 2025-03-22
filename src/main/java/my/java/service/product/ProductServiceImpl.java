// src/main/java/my/java/service/product/ProductServiceImpl.java
package my.java.service.product;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Product;
import my.java.repository.ProductRepository;
import my.java.service.base.BaseEntityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Реализация сервиса для управления продуктами
 */
@Service
@Slf4j
public class ProductServiceImpl extends BaseEntityService<Product, Long, ProductRepository> implements ProductService {

    public ProductServiceImpl(ProductRepository repository) {
        super(repository);
    }

    @Override
    protected void logSave(Product entity) {
        log.debug("Сохранение продукта: {}", entity.getProductName());
    }

    @Override
    public Product saveProduct(Product product) {
        return save(product);
    }

    @Override
    public int saveProducts(List<Product> products) {
        return saveAll(products);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findByProductIdAndClientId(String productId, Long clientId) {
        log.debug("Поиск продукта по productId: {} и clientId: {}", productId, clientId);
        return repository.findByProductId(productId)
                .filter(product -> product.getClientId().equals(clientId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByClientId(Long clientId) {
        log.debug("Поиск продуктов по clientId: {}", clientId);
        return repository.findByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByClientIdAndDataSource(Long clientId, String dataSource) {
        log.debug("Поиск продуктов по clientId: {} и dataSource: {}", clientId, dataSource);
        return repository.findByClientIdAndDataSource(clientId, dataSource);
    }

    @Override
    public void deleteProduct(Long id) {
        deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByFileIdAndClientId(Long fileId, Long clientId) {
        log.debug("Удаление продуктов по fileId: {} и clientId: {}", fileId, clientId);

        List<Product> productsToDelete = findProductsForDeletion(fileId, clientId);
        if (productsToDelete.isEmpty()) {
            return 0;
        }

        repository.deleteByFileIdAndClientId(fileId, clientId);
        return productsToDelete.size();
    }

    /**
     * Находит продукты для удаления по fileId и clientId
     *
     * @param fileId идентификатор файла
     * @param clientId идентификатор клиента
     * @return список продуктов для удаления
     */
    private List<Product> findProductsForDeletion(Long fileId, Long clientId) {
        return repository.findByClientId(clientId)
                .stream()
                .filter(product -> fileId.equals(product.getFileId()))
                .toList();
    }

    @Override
    @Transactional
    public Product upsertProduct(Product product) {
        log.debug("Обновление/создание продукта: {}", product.getProductName());

        if (product.getProductId() != null && product.getClientId() != null) {
            Optional<Product> existingProduct = findByProductIdAndClientId(
                    product.getProductId(), product.getClientId());

            if (existingProduct.isPresent()) {
                Product existing = existingProduct.get();
                copyProductFields(product, existing);
                return repository.save(existing);
            }
        }

        return repository.save(product);
    }

    /**
     * Копирует поля из исходного продукта в целевой продукт
     * @param source исходный продукт
     * @param target целевой продукт
     */
    private void copyProductFields(Product source, Product target) {
        target.setProductName(source.getProductName());
        target.setProductBrand(source.getProductBrand());
        target.setProductBar(source.getProductBar());
        target.setProductDescription(source.getProductDescription());
        target.setProductUrl(source.getProductUrl());
        target.setProductCategory1(source.getProductCategory1());
        target.setProductCategory2(source.getProductCategory2());
        target.setProductCategory3(source.getProductCategory3());
        target.setProductPrice(source.getProductPrice());
        target.setProductAnalog(source.getProductAnalog());
        target.setProductAdditional1(source.getProductAdditional1());
        target.setProductAdditional2(source.getProductAdditional2());
        target.setProductAdditional3(source.getProductAdditional3());
        target.setProductAdditional4(source.getProductAdditional4());
        target.setProductAdditional5(source.getProductAdditional5());
        target.setDataSource(source.getDataSource());
        target.setFileId(source.getFileId());
        // Не копируем связанные сущности и ID
    }
}