package by.zoomos.service.export.strategy;

import by.zoomos.model.entity.CompetitorData;
import by.zoomos.model.entity.Product;
import by.zoomos.model.entity.RegionData;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Стратегия экспорта данных в формат XLSX
 */
@Component
@Slf4j
public class XlsxExportStrategy implements ExportStrategy {

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
        log.info("Начало экспорта {} продуктов в XLSX", products.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Products");

            // Создаем стили
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            // Создаем заголовки
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            // Заполняем данные
            int rowNum = 1;
            for (Product product : products) {
                for (RegionData regionData : product.getRegionData()) {
                    for (CompetitorData competitorData : product.getCompetitorData()) {
                        Row row = sheet.createRow(rowNum++);
                        fillProductRow(row, product, regionData, competitorData,
                                dateStyle, numberStyle);
                    }
                }
            }

            // Автоматически подгоняем ширину колонок
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Записываем в ByteArray
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            log.info("Экспорт в XLSX успешно завершен");
            return new ByteArrayResource(outputStream.toByteArray());

        } catch (Exception e) {
            log.error("Ошибка при экспорте в XLSX", e);
            throw new RuntimeException("Ошибка при экспорте в XLSX", e);
        }
    }

    @Override
    public String getFileName() {
        return "products_export.xlsx";
    }

    @Override
    public String getContentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public boolean supports(String format) {
        return "xlsx".equalsIgnoreCase(format);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private void fillProductRow(Row row, Product product, RegionData regionData,
                                CompetitorData competitorData,
                                CellStyle dateStyle, CellStyle numberStyle) {
        int cellNum = 0;

        // Основные данные продукта
        row.createCell(cellNum++).setCellValue(product.getProductId());
        row.createCell(cellNum++).setCellValue(product.getModel());
        row.createCell(cellNum++).setCellValue(product.getBrand());

        Cell basePriceCell = row.createCell(cellNum++);
        if (product.getBasePrice() != null) {
            basePriceCell.setCellValue(product.getBasePrice().doubleValue());
            basePriceCell.setCellStyle(numberStyle);
        }

        // Региональные данные
        row.createCell(cellNum++).setCellValue(regionData.getRegion());

        Cell regionalPriceCell = row.createCell(cellNum++);
        if (regionData.getRegionalPrice() != null) {
            regionalPriceCell.setCellValue(regionData.getRegionalPrice().doubleValue());
            regionalPriceCell.setCellStyle(numberStyle);
        }

        if (regionData.getStockAmount() != null) {
            row.createCell(cellNum++).setCellValue(regionData.getStockAmount());
        } else {
            cellNum++;
        }

        row.createCell(cellNum++).setCellValue(regionData.getWarehouse());

        // Данные конкурента
        row.createCell(cellNum++).setCellValue(competitorData.getCompetitorName());
        row.createCell(cellNum++).setCellValue(competitorData.getCompetitorUrl());

        Cell competitorPriceCell = row.createCell(cellNum++);
        if (competitorData.getCompetitorPrice() != null) {
            competitorPriceCell.setCellValue(competitorData.getCompetitorPrice().doubleValue());
            competitorPriceCell.setCellStyle(numberStyle);
        }

        Cell promoPriceCell = row.createCell(cellNum++);
        if (competitorData.getCompetitorPromoPrice() != null) {
            promoPriceCell.setCellValue(competitorData.getCompetitorPromoPrice().doubleValue());
            promoPriceCell.setCellStyle(numberStyle);
        }

        // Даты
        if (product.getCreatedAt() != null) {
            Cell createdCell = row.createCell(cellNum++);
            createdCell.setCellValue(product.getCreatedAt().format(DATE_FORMATTER));
            createdCell.setCellStyle(dateStyle);
        } else {
            cellNum++;
        }

        if (product.getUpdatedAt() != null) {
            Cell updatedCell = row.createCell(cellNum);
            updatedCell.setCellValue(product.getUpdatedAt().format(DATE_FORMATTER));
            updatedCell.setCellStyle(dateStyle);
        }
    }
}