package by.zoomos.service.file.processor;

import by.zoomos.exception.FileProcessingException;
import by.zoomos.model.entity.CompetitorData;
import by.zoomos.model.entity.Product;
import by.zoomos.model.entity.RegionData;
import by.zoomos.service.ProcessingStatusService;
import by.zoomos.service.ProductService;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Процессор для обработки CSV файлов
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsvFileProcessor implements FileProcessor {

    private final ProductService productService;
    private final ProcessingStatusService statusService;
    private static final int BATCH_SIZE = 100;

    @Override
    public void process(InputStream inputStream, Long clientId, Long statusId) {
        log.info("Начало обработки CSV файла. StatusId: {}", statusId);
        List<Product> productBatch = new ArrayList<>(BATCH_SIZE);

        // Сначала подсчитаем количество строк
        int totalRows = countLines(inputStream) - 1; // Вычитаем строку заголовка
        log.info("Общее количество строк для обработки: {}", totalRows);
        statusService.updateProgress(statusId, 0, totalRows);

        // Сбрасываем inputStream для повторного чтения
        try {
            inputStream.reset();
        } catch (IOException e) {
            String errorMessage = "Ошибка при сбросе потока данных: " + e.getMessage();
            log.error(errorMessage, e);
            statusService.markFailed(statusId, errorMessage);
            throw new FileProcessingException(errorMessage, e);
        }

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .withSkipLines(0)
                .build()) {

            // Читаем заголовки
            String[] headers = reader.readNext();
            if (headers == null) {
                throw new FileProcessingException("CSV файл пуст");
            }

            // Создаем карту индексов колонок
            Map<String, Integer> headerMap = createHeaderMap(headers);
            validateHeaders(headerMap);

            // Читаем данные
            String[] line;
            int processedRows = 0;

            while ((line = reader.readNext()) != null) {
                try {
                    Product product = processLine(line, headerMap, clientId);
                    productBatch.add(product);

                    if (productBatch.size() >= BATCH_SIZE) {
                        productService.saveProductBatch(productBatch);
                        productBatch.clear();
                        processedRows += BATCH_SIZE;
                        statusService.updateProgress(statusId, processedRows, totalRows);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при обработке строки {}", processedRows + 1, e);
                }
                processedRows++;

                if (processedRows % BATCH_SIZE != 0) {
                    statusService.updateProgress(statusId, processedRows, totalRows);
                }
            }

            // Сохраняем оставшиеся продукты
            if (!productBatch.isEmpty()) {
                productService.saveProductBatch(productBatch);
                processedRows += productBatch.size();
                statusService.updateProgress(statusId, processedRows, totalRows);
            }

        } catch (IOException | CsvValidationException e) {
            String errorMessage = "Ошибка при обработке CSV файла: " + e.getMessage();
            log.error(errorMessage, e);
            statusService.markFailed(statusId, errorMessage);
            throw new FileProcessingException(errorMessage, e);
        }

        log.info("Завершение обработки CSV файла");
    }

    private int countLines(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        } catch (IOException e) {
            throw new FileProcessingException("Ошибка при подсчете строк в файле", e);
        }
    }


    @Override
    public boolean supports(String fileName) {
        return fileName != null &&
                (fileName.endsWith(".csv") || fileName.endsWith(".CSV"));
    }

    private Map<String, Integer> createHeaderMap(String[] headers) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            headerMap.put(headers[i].trim().toLowerCase(), i);
        }
        return headerMap;
    }

    private void validateHeaders(Map<String, Integer> headerMap) {
        List<String> requiredColumns = List.of(
                "product_id", "model", "brand", "base_price",
                "region", "regional_price", "stock_amount",
                "competitor_name", "competitor_price"
        );

        List<String> missingColumns = new ArrayList<>();
        for (String column : requiredColumns) {
            if (!headerMap.containsKey(column)) {
                missingColumns.add(column);
            }
        }

        if (!missingColumns.isEmpty()) {
            throw new FileProcessingException(
                    "Отсутствуют обязательные колонки: " + String.join(", ", missingColumns));
        }
    }

    private Product processLine(String[] line, Map<String, Integer> headerMap, Long clientId) {
        Product product = new Product();
        product.setClientId(clientId);
        product.setProductId(getValue(line, headerMap, "product_id"));
        product.setModel(getValue(line, headerMap, "model"));
        product.setBrand(getValue(line, headerMap, "brand"));
        product.setBasePrice(getBigDecimalValue(getValue(line, headerMap, "base_price")));

        // Добавляем региональные данные
        RegionData regionData = RegionData.of(
                getValue(line, headerMap, "region"),
                getBigDecimalValue(getValue(line, headerMap, "regional_price")),
                getIntegerValue(getValue(line, headerMap, "stock_amount")),
                getValue(line, headerMap, "warehouse")
        );
        product.addRegionData(regionData);

        // Добавляем данные конкурента
        CompetitorData competitorData = CompetitorData.of(
                getValue(line, headerMap, "competitor_name"),
                getValue(line, headerMap, "competitor_url"),
                getBigDecimalValue(getValue(line, headerMap, "competitor_price")),
                getBigDecimalValue(getValue(line, headerMap, "competitor_promo_price"))
        );
        product.addCompetitorData(competitorData);

        return product;
    }

    private String getValue(String[] line, Map<String, Integer> headerMap, String columnName) {
        Integer index = headerMap.get(columnName);
        if (index != null && index < line.length) {
            String value = line[index].trim();
            return StringUtils.hasText(value) ? value : null;
        }
        return null;
    }

    private BigDecimal getBigDecimalValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "."));
        } catch (NumberFormatException e) {
            log.warn("Ошибка преобразования в BigDecimal: {}", value);
            return null;
        }
    }

    private Integer getIntegerValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Ошибка преобразования в Integer: {}", value);
            return null;
        }
    }
}
