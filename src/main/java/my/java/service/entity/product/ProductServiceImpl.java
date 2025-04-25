// src/main/java/my/java/service/entity/product/ProductServiceImpl.java
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
        if (products == null || products.isEmpty()) return 0;
        log.debug("Сохранение {} продуктов", products.size());
        return productRepository.saveAll(products).size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findByProductIdAndClientId(String productId, Long clientId) {
        return productRepository.findByProductId(productId)
                .filter(product -> product.getClientId().equals(clientId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByClientId(Long clientId) {
        return productRepository.findByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByClientIdAndDataSource(Long clientId, String dataSource) {
        return productRepository.findByClientIdAndDataSource(clientId, dataSource);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByFileIdAndClientId(Long fileId, Long clientId) {
        List<Product> productsToDelete = productRepository.findByClientId(clientId)
                .stream()
                .filter(product -> fileId.equals(product.getFileId()))
                .toList();

        if (productsToDelete.isEmpty()) return 0;

        productRepository.deleteByFileIdAndClientId(fileId, clientId);
        return productsToDelete.size();
    }

    @Override
    @Transactional
    public Product upsertProduct(Product product) {
        if (product.getProductId() != null && product.getClientId() != null) {
            Optional<Product> existingProduct = findByProductIdAndClientId(
                    product.getProductId(), product.getClientId());

            if (existingProduct.isPresent()) {
                Product existing = existingProduct.get();
                copyProductFields(product, existing);
                return productRepository.save(existing);
            }
        }

        return productRepository.save(product);
    }

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
    }
}