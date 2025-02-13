package by.zoomos.service.validation;

import by.zoomos.exception.ValidationException;
import by.zoomos.model.entity.CompetitorData;
import by.zoomos.model.entity.Product;
import by.zoomos.model.entity.RegionData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Валидатор для продуктов
 */
@Component
@Slf4j
public class ProductValidator implements DataValidator<Product> {

    @Override
    public void validate(Product product) {
        log.debug("Валидация продукта: {}", product.getProductId());
        List<String> errors = new ArrayList<>();

        // Проверка основных полей продукта
        validateBasicFields(product, errors);

        // Проверка региональных данных
        validateRegionData(product, errors);

        // Проверка данных конкурентов
        validateCompetitorData(product, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException("Ошибки валидации продукта", errors);
        }
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return Product.class.isAssignableFrom(clazz);
    }

    private void validateBasicFields(Product product, List<String> errors) {
        // Проверка идентификатора продукта
        if (!StringUtils.hasText(product.getProductId())) {
            errors.add("Product ID не может быть пустым");
        }

        // Проверка модели
        if (!StringUtils.hasText(product.getModel())) {
            errors.add("Модель не может быть пустой");
        }

        // Проверка бренда
        if (!StringUtils.hasText(product.getBrand())) {
            errors.add("Бренд не может быть пустым");
        }

        // Проверка базовой цены
        if (product.getBasePrice() != null && product.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Базовая цена не может быть отрицательной");
        }

        // Проверка clientId
        if (product.getClientId() == null) {
            errors.add("Client ID не может быть пустым");
        }
    }

    private void validateRegionData(Product product, List<String> errors) {
        if (product.getRegionData() == null || product.getRegionData().isEmpty()) {
            errors.add("Региональные данные не могут быть пустыми");
            return;
        }

        for (RegionData regionData : product.getRegionData()) {
            // Проверка региона
            if (!StringUtils.hasText(regionData.getRegion())) {
                errors.add("Регион не может быть пустым");
            }

            // Проверка региональной цены
            if (regionData.getRegionalPrice() != null &&
                    regionData.getRegionalPrice().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Региональная цена не может быть отрицательной для региона: " +
                        regionData.getRegion());
            }

            // Проверка количества на складе
            if (regionData.getStockAmount() != null && regionData.getStockAmount() < 0) {
                errors.add("Количество на складе не может быть отрицательным для региона: " +
                        regionData.getRegion());
            }
        }
    }

    private void validateCompetitorData(Product product, List<String> errors) {
        if (product.getCompetitorData() == null || product.getCompetitorData().isEmpty()) {
            return; // Данные конкурентов могут отсутствовать
        }

        for (CompetitorData competitorData : product.getCompetitorData()) {
            // Проверка названия конкурента
            if (!StringUtils.hasText(competitorData.getCompetitorName())) {
                errors.add("Название конкурента не может быть пустым");
            }

            // Проверка цены конкурента
            if (competitorData.getCompetitorPrice() != null &&
                    competitorData.getCompetitorPrice().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Цена конкурента не может быть отрицательной для: " +
                        competitorData.getCompetitorName());
            }

            // Проверка промо-цены конкурента
            if (competitorData.getCompetitorPromoPrice() != null &&
                    competitorData.getCompetitorPromoPrice().compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Промо-цена конкурента не может быть отрицательной для: " +
                        competitorData.getCompetitorName());
            }

            // Проверка соотношения цен
            if (competitorData.getCompetitorPrice() != null &&
                    competitorData.getCompetitorPromoPrice() != null &&
                    competitorData.getCompetitorPromoPrice()
                            .compareTo(competitorData.getCompetitorPrice()) > 0) {
                errors.add("Промо-цена конкурента не может быть больше обычной цены для: " +
                        competitorData.getCompetitorName());
            }
        }
    }
}