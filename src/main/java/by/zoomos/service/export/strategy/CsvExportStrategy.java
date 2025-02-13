package by.zoomos.service.export.strategy;

import by.zoomos.model.entity.CompetitorData;
import by.zoomos.model.entity.Product;
import by.zoomos.model.entity.RegionData;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Стратегия экспорта данных в формат CSV
 */
@Component
@Slf4j
public class CsvExportStrategy implements ExportStrategy {

    private static final String[] HEADERS = {
            "Product ID", "Model", "Brand", "Base Price",
            "Region", "Regional Price", "Stock Amount", "Warehouse",
            "Competitor Name", "Competitor URL", "Competitor Price", "Competitor Promo Price",
            "Created At", "Updated At"
    };

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Resource export(List<Product> products, Map<String, String> params) {
        log.info("Начало экспорта {} продуктов в CSV", products.size());

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

            try (CSVWriter writer = new CSVWriter(outputStreamWriter,
                    CSVWriter.DEFAULT_SEPARATOR,
                    CSVWriter.DEFAULT_QUOTE_CHARACTER,
                    CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                    CSVWriter.DEFAULT_LINE_END)) {

                // Записываем заголовки
                writer.writeNext(HEADERS);

                // Записываем данные
                for (Product product : products) {
                    List<String[]> rows = convertProductToRows(product);
                    writer.writeAll(rows);
                }
            }

            log.info("Экспорт в CSV успешно завершен");
            return new ByteArrayResource(outputStream.toByteArray());

        } catch (Exception e) {
            log.error("Ошибка при экспорте в CSV", e);
            throw new RuntimeException("Ошибка при экспорте в CSV", e);
        }
    }

    @Override
    public String getFileName() {
        return "products_export.csv";
    }

    @Override
    public String getContentType() {
        return "text/csv";
    }

    @Override
    public boolean supports(String format) {
        return "csv".equalsIgnoreCase(format);
    }

    private List<String[]> convertProductToRows(Product product) {
        List<String[]> rows = new ArrayList<>();

        for (RegionData regionData : product.getRegionData()) {
            for (CompetitorData competitorData : product.getCompetitorData()) {
                String[] row = new String[HEADERS.length];
                int i = 0;

                // Основные данные продукта
                row[i++] = product.getProductId();
                row[i++] = product.getModel();
                row[i++] = product.getBrand();
                row[i++] = product.getBasePrice() != null ?
                        product.getBasePrice().toString() : "";

                // Региональные данные
                row[i++] = regionData.getRegion();
                row[i++] = regionData.getRegionalPrice() != null ?
                        regionData.getRegionalPrice().toString() : "";
                row[i++] = regionData.getStockAmount() != null ?
                        regionData.getStockAmount().toString() : "";
                row[i++] = regionData.getWarehouse();

                // Данные конкурента
                row[i++] = competitorData.getCompetitorName();
                row[i++] = competitorData.getCompetitorUrl();
                row[i++] = competitorData.getCompetitorPrice() != null ?
                        competitorData.getCompetitorPrice().toString() : "";
                row[i++] = competitorData.getCompetitorPromoPrice() != null ?
                        competitorData.getCompetitorPromoPrice().toString() : "";

                // Даты
                row[i++] = product.getCreatedAt() != null ?
                        product.getCreatedAt().format(DATE_FORMATTER) : "";
                row[i] = product.getUpdatedAt() != null ?
                        product.getUpdatedAt().format(DATE_FORMATTER) : "";

                rows.add(row);
            }
        }

        return rows;
    }
}