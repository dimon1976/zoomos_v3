// src/main/java/my/java/service/product/ProductServiceImpl.java
package my.java.service.entity.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Product;
import my.java.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public Product saveProduct(Product product) {
        log.debug("Сохранение продукта: {}", product.getProductName());
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public int saveProducts(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return 0;
        }

        log.debug("Сохранение {} продуктов", products.size());
        List<Product> savedProducts = productRepository.saveAll(products);
        return savedProducts.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        log.debug("Поиск продукта по ID: {}", id);
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findByProductIdAndClientId(String productId, Long clientId) {
        log.debug("Поиск продукта по productId: {} и clientId: {}", productId, clientId);
        return productRepository.findByProductId(productId)
                .filter(product -> product.getClientId().equals(clientId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByClientId(Long clientId) {
        log.debug("Поиск продуктов по clientId: {}", clientId);
        return productRepository.findByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByClientIdAndDataSource(Long clientId, String dataSource) {
        log.debug("Поиск продуктов по clientId: {} и dataSource: {}", clientId, dataSource);
        return productRepository.findByClientIdAndDataSource(clientId, dataSource);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        log.debug("Удаление продукта по ID: {}", id);
        productRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByFileIdAndClientId(Long fileId, Long clientId) {
        log.debug("Удаление продуктов по fileId: {} и clientId: {}", fileId, clientId);

        // Сначала подсчитываем количество продуктов для удаления
        List<Product> productsToDelete = productRepository.findByClientId(clientId)
                .stream()
                .filter(product -> fileId.equals(product.getFileId()))
                .toList();

        if (productsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем продукты
        productRepository.deleteByFileIdAndClientId(fileId, clientId);
        return productsToDelete.size();
    }

    @Override
    @Transactional
    public Product upsertProduct(Product product) {
        log.debug("Обновление/создание продукта: {}", product.getProductName());

        // Проверяем существование продукта по внешнему ID и клиенту
        if (product.getProductId() != null && product.getClientId() != null) {
            Optional<Product> existingProduct = findByProductIdAndClientId(
                    product.getProductId(), product.getClientId());

            if (existingProduct.isPresent()) {
                // Обновляем существующий продукт
                Product existing = existingProduct.get();
                // Обновляем все поля, кроме ID и созданных связей
                copyProductFields(product, existing);
                return productRepository.save(existing);
            }
        }

        // Создаем новый продукт
        return productRepository.save(product);
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