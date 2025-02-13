package by.zoomos.service;

import by.zoomos.exception.ProductNotFoundException;
import by.zoomos.model.entity.CompetitorData;
import by.zoomos.model.entity.Product;
import by.zoomos.model.entity.RegionData;
import by.zoomos.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Сервис для работы с продуктами
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    /**
     * Сохраняет продукт в базу данных
     *
     * @param product продукт для сохранения
     * @return сохраненный продукт
     */
    @Transactional
    public Product saveProduct(Product product) {
        log.debug("Сохранение продукта: {}", product.getProductId());
        try {
            return productRepository.save(product);
        } catch (Exception e) {
            log.error("Ошибка при сохранении продукта: {}", product.getProductId(), e);
            throw e;
        }
    }

    /**
     * Пакетное сохранение продуктов
     *
     * @param products список продуктов для сохранения
     */
    @Transactional
    public void saveProductBatch(List<Product> products) {
        log.debug("Пакетное сохранение {} продуктов", products.size());
        int batchSize = 50;
        try {
            for (int i = 0; i < products.size(); i++) {
                entityManager.persist(products.get(i));
                if (i % batchSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при пакетном сохранении продуктов", e);
            throw e;
        }
    }

    /**
     * Находит продукт по productId со всеми связанными данными
     *
     * @param productId идентификатор продукта
     * @return найденный продукт
     * @throws ProductNotFoundException если продукт не найден
     */
    @Transactional(readOnly = true)
    public Product findProductWithAllData(String productId) {
        log.debug("Поиск продукта по productId: {}", productId);
        return productRepository.findByProductIdWithAllData(productId)
                .orElseThrow(() -> new ProductNotFoundException("Продукт не найден: " + productId));
    }

    /**
     * Получает поток продуктов по clientId
     *
     * @param clientId идентификатор клиента
     * @return поток продуктов
     */
    @Transactional(readOnly = true)
    public Stream<Product> streamProductsByClientId(Long clientId) {
        log.debug("Получение потока продуктов для клиента: {}", clientId);
        return productRepository.streamByClientIdWithAllData(clientId);
    }

    /**
     * Обновляет продукт
     *
     * @param product продукт для обновления
     * @return обновленный продукт
     */
    @Transactional
    public Product updateProduct(Product product) {
        log.debug("Обновление продукта: {}", product.getProductId());
        findProductWithAllData(product.getProductId()); // Проверяем существование
        return saveProduct(product);
    }

    /**
     * Удаляет продукт по productId
     *
     * @param productId идентификатор продукта
     */
    @Transactional
    public void deleteProduct(String productId) {
        log.debug("Удаление продукта: {}", productId);
        Product product = findProductWithAllData(productId);
        productRepository.delete(product);
    }

    /**
     * Добавляет региональные данные к продукту
     *
     * @param productId идентификатор продукта
     * @param regionData региональные данные
     * @return обновленный продукт
     */
    @Transactional
    public Product addRegionData(String productId, RegionData regionData) {
        log.debug("Добавление региональных данных к продукту: {}", productId);
        Product product = findProductWithAllData(productId);
        product.addRegionData(regionData);
        return saveProduct(product);
    }

    /**
     * Добавляет данные конкурента к продукту
     *
     * @param productId идентификатор продукта
     * @param competitorData данные конкурента
     * @return обновленный продукт
     */
    @Transactional
    public Product addCompetitorData(String productId, CompetitorData competitorData) {
        log.debug("Добавление данных конкурента к продукту: {}", productId);
        Product product = findProductWithAllData(productId);
        product.addCompetitorData(competitorData);
        return saveProduct(product);
    }

    /**
     * Поиск продуктов с применением фильтров
     *
     * @param productId ID продукта
     * @param brand бренд
     * @param region регион
     * @param minPrice минимальная цена
     * @param maxPrice максимальная цена
     * @param pageable параметры пагинации
     * @return страница с продуктами
     */
    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String productId,
                                        String brand,
                                        String region,
                                        BigDecimal minPrice,
                                        BigDecimal maxPrice,
                                        Pageable pageable) {
        log.debug("Поиск продуктов с фильтрами");

        Specification<Product> spec = Specification.where(null);

        // Добавляем фильтры, если они указаны
        if (StringUtils.hasText(productId)) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("productId")),
                            "%" + productId.toLowerCase() + "%"));
        }

        if (StringUtils.hasText(brand)) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("brand")),
                            "%" + brand.toLowerCase() + "%"));
        }

        if (StringUtils.hasText(region)) {
            spec = spec.and((root, query, cb) -> {
                Join<Product, RegionData> regionJoin = root.join("regionData");
                return cb.like(cb.lower(regionJoin.get("region")),
                        "%" + region.toLowerCase() + "%");
            });
        }

        if (minPrice != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("basePrice"), minPrice));
        }

        if (maxPrice != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("basePrice"), maxPrice));
        }

        // Получаем результаты с подгрузкой связанных данных
//        return productRepository.findAll(spec, pageable);
        return null;
    }
}
