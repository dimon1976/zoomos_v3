// src/main/java/my/java/service/file/importer/CsvImportService.java
package my.java.service.file.importer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.FieldMapping;
import my.java.model.FileOperation;
import my.java.model.entity.ImportableEntity;
import my.java.repository.FileOperationRepository;
import my.java.service.file.importer.strategy.DuplicateHandlingStrategy;
import my.java.service.file.importer.strategy.DuplicateStrategyFactory;
import my.java.service.mapping.FieldMappingService;
import my.java.util.transformer.ValueTransformerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Упрощенный сервис для импорта CSV файлов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CsvImportService {

    private final FileOperationRepository fileOperationRepository;
    private final FieldMappingService fieldMappingService;
    private final ValueTransformerFactory transformerFactory;
    private final DuplicateStrategyFactory strategyFactory;
    private final BatchEntityProcessor batchEntityProcessor;

    @Value("${application.import.batch-size:1000}")
    private int batchSize;

    @Value("${application.import.progress-update-interval:100}")
    private int progressUpdateInterval;

    /**
     * Асинхронный импорт CSV файла
     */
    @Async("fileProcessingExecutor")
    public CompletableFuture<FileOperation> importCsvAsync(
            Path csvFile,
            FieldMapping mapping,
            Client client,
            FileOperation operation) {

        log.info("Starting async CSV import for operation {}", operation.getId());

        try {
            operation.markAsProcessing();
            fileOperationRepository.save(operation);

            ImportResult result = processImport(csvFile, mapping, client, operation);

            operation.markAsCompleted(result.getTotalProcessed());
            operation.setProcessedRecords(result.getTotalProcessed());
            operation.setTotalRecords((int) result.getTotalRecords());

            fileOperationRepository.save(operation);

            log.info("CSV import completed successfully. Processed {} records", result.getTotalProcessed());
            return CompletableFuture.completedFuture(operation);

        } catch (Exception e) {
            log.error("Error during CSV import for operation {}: {}", operation.getId(), e.getMessage(), e);
            operation.markAsFailed("Ошибка импорта: " + e.getMessage());
            fileOperationRepository.save(operation);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Основная логика обработки импорта
     */
    private ImportResult processImport(Path csvFile, FieldMapping mapping,
                                       Client client, FileOperation operation) throws IOException {

        log.info("Processing import for file: {}, size: {} bytes", csvFile, Files.size(csvFile));

        ImportResult result = new ImportResult();
        List<Map<String, String>> batchData = new ArrayList<>();

        Charset charset = getCharset(mapping.getFileEncoding());

        try (BufferedReader reader = createSafeBufferedReader(csvFile, charset)) {
            String line;
            String[] headers = null;
            int lineNumber = 0;
            int processedCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                // Первая строка - заголовки
                if (headers == null) {
                    headers = parseCsvLine(line, mapping);
                    result.setTotalRecords(estimateRecordCount(csvFile) - 1);
                    log.info("Found {} headers, estimated {} records", headers.length, result.getTotalRecords());
                    continue;
                }

                try {
                    String[] values = parseCsvLine(line, mapping);
                    Map<String, String> rowData = createRowMap(headers, values);
                    batchData.add(rowData);

                    // Обрабатываем пакет данных
                    if (batchData.size() >= batchSize) {
                        BatchProcessResult batchResult = processBatch(batchData, mapping, client, operation);
                        result.addBatchResult(batchResult);

                        processedCount += batchData.size();
                        batchData.clear();

                        // Обновляем прогресс
                        if (processedCount % progressUpdateInterval == 0) {
                            updateProgress(operation, processedCount, result.getTotalRecords());
                        }
                    }

                } catch (Exception e) {
                    log.warn("Error processing line {}: {}", lineNumber, e.getMessage());
                    result.addError(lineNumber, e.getMessage());
                }
            }

            // Обрабатываем остаток данных
            if (!batchData.isEmpty()) {
                BatchProcessResult batchResult = processBatch(batchData, mapping, client, operation);
                result.addBatchResult(batchResult);
                processedCount += batchData.size();
            }

            updateProgress(operation, processedCount, result.getTotalRecords());
            result.setTotalProcessed(processedCount);
            log.info("Import completed. Processed {} records", processedCount);

        } catch (IOException e) {
            log.error("Error reading file: {}", e.getMessage());
            throw e;
        }

        return result;
    }

    /**
     * Обработка пакета данных с новой упрощенной логикой
     */
    public BatchProcessResult processBatch(List<Map<String, String>> batchData,
                                           FieldMapping mapping, Client client, FileOperation operation) {

        log.info("=== Processing batch of {} records ===", batchData.size());
        BatchProcessResult result = new BatchProcessResult();

        // Создаем контейнер для данных
        EntityRelationshipHolder holder = new EntityRelationshipHolder();

        // Парсим данные и заполняем holder
        for (int i = 0; i < batchData.size(); i++) {
            Map<String, String> rowData = batchData.get(i);
            try {
                String productId = extractProductIdFromRow(rowData, mapping);
                log.debug("Row {}: extracted productId={}", i, productId);

                Map<String, ImportableEntity> entities = fieldMappingService.applyMapping(mapping, rowData);
                log.debug("Row {}: created {} entities", i, entities.size());

                distributeEntitiesWithRelationships(entities, productId, client, operation.getId(), holder);
                result.incrementProcessed();

            } catch (Exception e) {
                log.warn("Error processing row {}: {}", i, e.getMessage(), e);
                result.incrementFailed();
                result.addError(e.getMessage());
            }
        }

        log.info("After parsing: {} products, {} competitors, {} regions in holder",
                holder.getProductsByProductId().size(),
                holder.getCompetitorsByProductId().size(),
                holder.getRegionsByProductId().size());

        // Применяем стратегию дубликатов
        log.info("Applying {} strategy...", mapping.getDuplicateStrategy());
        DuplicateHandlingStrategy strategy = strategyFactory.createStrategy(mapping.getDuplicateStrategy());
        BatchSaveResult saveResult = strategy.process(holder, client.getId());
        result.addSaveResult(saveResult);

        log.info("=== Batch processing complete: saved={}, failed={} ===",
                saveResult.getSaved(), saveResult.getFailed());

        return result;
    }

    /**
     * Распределение сущностей с сохранением связей
     */
    private void distributeEntitiesWithRelationships(Map<String, ImportableEntity> entities,
                                                     String productId,
                                                     Client client,
                                                     Long operationId,
                                                     EntityRelationshipHolder holder) {

        log.debug("Distributing {} entities for productId={}", entities.size(), productId);

        for (Map.Entry<String, ImportableEntity> entry : entities.entrySet()) {
            ImportableEntity entity = entry.getValue();
            String entityType = entry.getKey();

            log.debug("Processing entity type: {}", entityType);

            // Устанавливаем общие поля
            setCommonFields(entity, client.getId(), operationId);

            // Добавляем в holder с сохранением связей только если сущность содержит данные
            switch (entityType) {
                case "PRODUCT" -> {
                    var product = (my.java.model.entity.Product) entity;
                    log.debug("Product: id={}, name={}", product.getProductId(), product.getProductName());

                    // Проверяем, что продукт содержит хотя бы минимальные данные
                    if (hasValidProductData(product)) {
                        log.debug("Adding valid product to holder");
                        holder.addProduct(product);
                    } else {
                        log.debug("Skipping empty product");
                    }
                }
                case "COMPETITOR" -> {
                    var competitor = (my.java.model.entity.Competitor) entity;
                    log.debug("Competitor: name={}, linkedTo={}", competitor.getCompetitorName(), productId);

                    // Проверяем, что конкурент содержит данные
                    if (hasValidCompetitorData(competitor)) {
                        log.debug("Adding valid competitor to holder");
                        holder.addCompetitor(productId, competitor);
                    } else {
                        log.debug("Skipping empty competitor");
                    }
                }
                case "REGION" -> {
                    var region = (my.java.model.entity.Region) entity;
                    log.debug("Region: name={}, address={}, linkedTo={}", region.getRegion(), region.getRegionAddress(), productId);

                    // Проверяем, что регион содержит данные
                    if (hasValidRegionData(region)) {
                        log.debug("Adding valid region to holder");
                        holder.addRegion(productId, region);
                    } else {
                        log.debug("Skipping empty region");
                    }
                }
                default -> log.warn("Unknown entity type: {}", entityType);
            }
        }

        log.debug("Holder now contains: {} products, {} competitor groups, {} region groups",
                holder.getProductsByProductId().size(),
                holder.getCompetitorsByProductId().size(),
                holder.getRegionsByProductId().size());
    }

    /**
     * Проверяет, содержит ли продукт валидные данные
     */
    private boolean hasValidProductData(my.java.model.entity.Product product) {
        // Продукт считается валидным если есть хотя бы одно из ключевых полей
        return hasValue(product.getProductId()) ||
                hasValue(product.getProductName()) ||
                hasValue(product.getProductBar());
    }

    /**
     * Проверяет, содержит ли конкурент валидные данные
     */
    private boolean hasValidCompetitorData(my.java.model.entity.Competitor competitor) {
        // Конкурент считается валидным если есть хотя бы название или цена
        return hasValue(competitor.getCompetitorName()) ||
                hasValue(competitor.getCompetitorPrice());
    }

    /**
     * Проверяет, содержит ли регион валидные данные
     */
    private boolean hasValidRegionData(my.java.model.entity.Region region) {
        // Регион считается валидным если есть название или адрес
        return hasValue(region.getRegion()) ||
                hasValue(region.getRegionAddress());
    }

    /**
     * Проверяет, что строка не null и не пустая
     */
    private boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Извлекает productId из строки CSV данных
     */
    private String extractProductIdFromRow(Map<String, String> rowData, FieldMapping mapping) {
        log.debug("Extracting productId from row with {} fields", rowData.size());
        log.debug("Available fields: {}", rowData.keySet());
        log.debug("Sample values: {}",
                rowData.entrySet().stream()
                        .limit(3)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue() != null ? e.getValue() : "NULL"
                        )));

        // Находим маппинг для productId
        var productIdMappings = mapping.getDetails().stream()
                .filter(detail -> "productId".equals(detail.getTargetField()) &&
                        ("PRODUCT".equals(detail.getTargetEntity()) || detail.getTargetEntity() == null))
                .collect(Collectors.toList());

        log.debug("Found {} productId mappings", productIdMappings.size());

        for (var detail : productIdMappings) {
            String sourceField = detail.getSourceField();
            String value = rowData.get(sourceField);
            log.debug("Checking mapping: '{}' -> '{}' = '{}'", sourceField, detail.getTargetField(), value);

            if (value != null && !value.trim().isEmpty()) {
                String result = value.trim();
                log.debug("Found productId: '{}'", result);
                return result;
            }
        }

        log.debug("No valid productId found in row");
        return null;
    }

    /**
     * Установка общих полей
     */
    private void setCommonFields(ImportableEntity entity, Long clientId, Long operationId) {
        entity.setClientId(clientId);
        entity.setTransformerFactory(transformerFactory);

        if (entity instanceof my.java.model.entity.Product product) {
            product.setOperationId(operationId);
        }
    }

    // === Вспомогательные методы (не изменились) ===

    private Charset getCharset(String encoding) {
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            log.error("Invalid encoding '{}', falling back to UTF-8", encoding);
            return StandardCharsets.UTF_8;
        }
    }

    private BufferedReader createSafeBufferedReader(Path filePath, Charset charset) throws IOException {
        try {
            return Files.newBufferedReader(filePath, charset);
        } catch (IOException e) {
            log.warn("Failed to read with {}, trying UTF-8: {}", charset, e.getMessage());
            try {
                return Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
            } catch (IOException e2) {
                log.warn("Failed to read with UTF-8, trying ISO-8859-1: {}", e2.getMessage());
                return Files.newBufferedReader(filePath, StandardCharsets.ISO_8859_1);
            }
        }
    }

    private String[] parseCsvLine(String line, FieldMapping mapping) {
        String delimiter = mapping.getCsvDelimiter();
        String quoteChar = mapping.getCsvQuoteChar();

        log.debug("Parsing CSV line with delimiter='{}', quote='{}'", delimiter, quoteChar);
        log.debug("Line to parse: {}", line.substring(0, Math.min(100, line.length())));

        // Простой парсер CSV с поддержкой кавычек
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        char delimiterChar = delimiter.charAt(0);
        char quoteCharChar = quoteChar.charAt(0);

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == quoteCharChar) {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == quoteCharChar) {
                    // Удвоенная кавычка - добавляем одну
                    currentField.append(quoteCharChar);
                    i++; // Пропускаем следующую кавычку
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (delimiterChar == c && !inQuotes) {
                String field = currentField.toString().trim();
                fields.add(field);
                log.trace("Parsed field: '{}'", field);
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }

        // Добавляем последнее поле
        String lastField = currentField.toString().trim();
        fields.add(lastField);
        log.trace("Last field: '{}'", lastField);

        String[] result = fields.toArray(new String[0]);
        log.debug("Parsed {} fields from CSV line", result.length);

        return result;
    }

    private Map<String, String> createRowMap(String[] headers, String[] values) {
        Map<String, String> rowData = new HashMap<>();
        for (int i = 0; i < headers.length && i < values.length; i++) {
            String header = headers[i];
            String value = values[i];
            if (header != null && !header.isEmpty()) {
                rowData.put(header, value);
            }
        }
        return rowData;
    }

    private long estimateRecordCount(Path csvFile) {
        try {
            long fileSize = Files.size(csvFile);
            long estimatedLines = fileSize / 100;
            return Math.max(1, estimatedLines);
        } catch (IOException e) {
            log.warn("Could not estimate record count: {}", e.getMessage());
            return 1000;
        }
    }

    private void updateProgress(FileOperation operation, int processed, long total) {
        int progress = total > 0 ? (int) ((processed * 100) / total) : 0;
        progress = Math.min(100, Math.max(0, progress));

        operation.setProcessedRecords(processed);
        operation.setProcessingProgress(progress);

        try {
            fileOperationRepository.save(operation);
            log.debug("Progress updated: {}/{} ({}%)", processed, total, progress);
        } catch (Exception e) {
            log.warn("Failed to update progress: {}", e.getMessage());
        }
    }

    /**
     * Результат импорта
     */
    public static class ImportResult {
        @Getter
        private int totalProcessed = 0;
        @Getter
        private long totalRecords = 0;
        @Getter
        private final List<String> errors = new ArrayList<>();
        private final List<BatchProcessResult> batchResults = new ArrayList<>();

        public void setTotalProcessed(int totalProcessed) {
            this.totalProcessed = totalProcessed;
        }

        public void setTotalRecords(long totalRecords) {
            this.totalRecords = totalRecords;
        }

        public void addError(int lineNumber, String error) {
            errors.add("Line " + lineNumber + ": " + error);
        }

        public void addBatchResult(BatchProcessResult result) {
            batchResults.add(result);
        }
    }

    /**
     * Результат обработки пакета
     */
    public static class BatchProcessResult {
        @Getter
        private int processed = 0;
        @Getter
        private int failed = 0;
        private final List<String> errors = new ArrayList<>();
        private final List<BatchSaveResult> saveResults = new ArrayList<>();

        public void incrementProcessed() {
            processed++;
        }

        public void incrementFailed() {
            failed++;
        }

        public void addError(String error) {
            errors.add(error);
        }

        public void addSaveResult(BatchSaveResult result) {
            saveResults.add(result);
        }
    }
}