// src/main/java/my/java/controller/EntityDataController.java
package my.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.MarketData;
import my.java.model.entity.Product;
import my.java.service.market.MarketDataService;
import my.java.service.product.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для API работы с данными сущностей
 */
@RestController
@RequestMapping("/api/entities")
@RequiredArgsConstructor
@Slf4j
public class EntityDataController {

    private final ProductService productService;
    private final MarketDataService marketDataService;


    /**
     * Получение продуктов по клиенту
     */
    @GetMapping("/products/client/{clientId}")
    public ResponseEntity<List<Product>> getProductsByClient(@PathVariable Long clientId) {
        log.debug("GET request to get products by client: {}", clientId);
        List<Product> products = productService.findByClientId(clientId);
        return ResponseEntity.ok(products);
    }

    /**
     * Получение данных регионов по клиенту
     */
    @GetMapping("/regions/client/{clientId}")
    public ResponseEntity<List<MarketData>> getRegionsByClient(@PathVariable Long clientId) {
        log.debug("GET request to get regions by client: {}", clientId);
        List<MarketData> regions = marketDataService.findByClientId(clientId);
        return ResponseEntity.ok(regions);
    }

    /**
     * Получение данных конкурентов по клиенту
     */
    @GetMapping("/competitors/client/{clientId}")
    public ResponseEntity<List<MarketData>> getCompetitorsByClient(@PathVariable Long clientId) {
        log.debug("GET request to get competitors by client: {}", clientId);
        List<MarketData> competitors = marketDataService.findByClientId(clientId);
        return ResponseEntity.ok(competitors);
    }

    /**
     * Удаление данных, связанных с файлом
     */
    @DeleteMapping("/file/{fileId}/client/{clientId}")
    public ResponseEntity<Map<String, Integer>> deleteDataByFile(
            @PathVariable Long fileId,
            @PathVariable Long clientId) {
        log.debug("DELETE request to delete data by fileId: {} and clientId: {}", fileId, clientId);

        // Удаляем продукты и связанные с ними данные регионов и конкурентов
        int productsDeleted = productService.deleteByFileIdAndClientId(fileId, clientId);

        Map<String, Integer> result = Map.of(
                "productsDeleted", productsDeleted
        );

        return ResponseEntity.ok(result);
    }
}