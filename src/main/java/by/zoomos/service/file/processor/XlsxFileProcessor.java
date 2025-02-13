package by.zoomos.service.file.processor;

import by.zoomos.exception.FileProcessingException;
import by.zoomos.model.entity.CompetitorData;
import by.zoomos.model.entity.Product;
import by.zoomos.model.entity.RegionData;
import by.zoomos.service.ProcessingStatusService;
import by.zoomos.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Процессор для обработки XLSX файлов
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class XlsxFileProcessor implements FileProcessor {

    private final ProductService productService;
    private final ProcessingStatusService statusService;
    private static final int BATCH_SIZE = 100;

    @Override
    public void process(InputStream inputStream, Long clientId, Long statusId) {
        log.info("Начало обработки XLSX файла. StatusId: {}", statusId);
        List<Product> productBatch = new ArrayList<>(BATCH_SIZE);

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            // Проверяем наличие всех необходимых колонок
            validateHeaders(headerRow);

            // Получаем общее количество строк для обработки
            int totalRows = sheet.getLastRowNum();
            statusService.updateProgress(statusId, 0, totalRows);
            log.info("Общее количество строк для обработки: {}", totalRows);

            // Обрабатываем строки
            int processedRows = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    Product product = processRow(row, clientId);
                    productBatch.add(product);

                    if (productBatch.size() >= BATCH_SIZE) {
                        productService.saveProductBatch(productBatch);
                        productBatch.clear();
                        processedRows += BATCH_SIZE;
                        statusService.updateProgress(statusId, processedRows, totalRows);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при обработке строки {}", i, e);
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

        } catch (Exception e) {
            String errorMessage = "Ошибка при обработке XLSX файла: " + e.getMessage();
            log.error(errorMessage, e);
            statusService.markFailed(statusId, errorMessage);
            throw new FileProcessingException(errorMessage, e);
        }

        log.info("Завершение обработки XLSX файла");
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null &&
                (fileName.endsWith(".xlsx") || fileName.endsWith(".XLSX"));
    }

    private void validateHeaders(Row headerRow) {
        List<String> requiredColumns = List.of(
                "product_id", "model", "brand", "base_price",
                "region", "regional_price", "stock_amount",
                "competitor_name", "competitor_price"
        );

        for (String column : requiredColumns) {
            boolean found = false;
            for (Cell cell : headerRow) {
                if (cell.getStringCellValue().equalsIgnoreCase(column)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new FileProcessingException("Отсутствует обязательная колонка: " + column);
            }
        }
    }

    private Product processRow(Row row, Long clientId) {
        Product product = new Product();
        product.setClientId(clientId);
        product.setProductId(getCellStringValue(row.getCell(0)));
        product.setModel(getCellStringValue(row.getCell(1)));
        product.setBrand(getCellStringValue(row.getCell(2)));
        product.setBasePrice(getCellBigDecimalValue(row.getCell(3)));

        // Добавляем региональные данные
        RegionData regionData = RegionData.of(
                getCellStringValue(row.getCell(4)),
                getCellBigDecimalValue(row.getCell(5)),
                getCellIntegerValue(row.getCell(6)),
                getCellStringValue(row.getCell(7))
        );
        product.addRegionData(regionData);

        // Добавляем данные конкурента
        CompetitorData competitorData = CompetitorData.of(
                getCellStringValue(row.getCell(8)),
                getCellStringValue(row.getCell(9)),
                getCellBigDecimalValue(row.getCell(10)),
                getCellBigDecimalValue(row.getCell(11))
        );
        product.addCompetitorData(competitorData);

        return product;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return null;
        }
    }

    private BigDecimal getCellBigDecimalValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                String value = cell.getStringCellValue();
                return StringUtils.hasText(value) ? new BigDecimal(value) : null;
            default:
                return null;
        }
    }

    private Integer getCellIntegerValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case NUMERIC:
                return (int) cell.getNumericCellValue();
            case STRING:
                String value = cell.getStringCellValue();
                return StringUtils.hasText(value) ? Integer.parseInt(value) : null;
            default:
                return null;
        }
    }
}
