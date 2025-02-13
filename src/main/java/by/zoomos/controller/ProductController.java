package by.zoomos.controller;

import by.zoomos.model.entity.Product;
import by.zoomos.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * REST контроллер для работы с продуктами
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;

    /**
     * Получает продукт по productId
     *
     * @param productId идентификатор продукта
     * @return найденный продукт
     */
    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable String productId) {
        log.info("Получение продукта по id: {}", productId);
        return ResponseEntity.ok(productService.findProductWithAllData(productId));
    }

    /**
     * Создает новый продукт
     *
     * @param product продукт для создания
     * @return созданный продукт
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        log.info("Создание нового продукта: {}", product.getProductId());
        return ResponseEntity.ok(productService.saveProduct(product));
    }

    /**
     * Обновляет существующий продукт
     *
     * @param productId идентификатор продукта
     * @param product обновленный продукт
     * @return обновленный продукт
     */
    @PutMapping("/{productId}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable String productId,
            @RequestBody Product product) {
        log.info("Обновление продукта: {}", productId);
        product.setProductId(productId);
        return ResponseEntity.ok(productService.updateProduct(product));
    }

    /**
     * Удаляет продукт
     *
     * @param productId идентификатор продукта
     * @return статус выполнения операции
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String productId) {
        log.info("Удаление продукта: {}", productId);
        productService.deleteProduct(productId);
        return ResponseEntity.ok().build();
    }

    /**
     * Поиск продуктов с фильтрацией
     *
     * @param productId ID продукта (опционально)
     * @param brand бренд (опционально)
     * @param region регион (опционально)
     * @param minPrice минимальная цена (опционально)
     * @param maxPrice максимальная цена (опционально)
     * @param pageable параметры пагинации
     * @return страница с продуктами
     */
    @GetMapping("/search")
    public ResponseEntity<Page<Product>> searchProducts(
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) {
        log.debug("Поиск продуктов с фильтрами. ProductId: {}, Brand: {}, Region: {}, Price: {}-{}",
                productId, brand, region, minPrice, maxPrice);

        return ResponseEntity.ok(productService.searchProducts(
                productId, brand, region, minPrice, maxPrice, pageable));
    }
}
