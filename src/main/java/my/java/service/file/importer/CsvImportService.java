// src/main/java/my/java/service/file/importer/CsvImportService.java
package my.java.service.file.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.FieldMapping;
import my.java.model.FieldMappingDetail;
import my.java.model.FileOperation;
import my.java.model.entity.Competitor;
import my.java.model.entity.ImportableEntity;
import my.java.model.entity.Product;
import my.java.model.entity.Region;
import my.java.repository.FileOperationRepository;
import my.java.repository.ProductRepository;
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
 * Асинхронный сервис для импорта CSV файлов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CsvImportService {

    private final FileOperationRepository fileOperationRepository;
    private final FieldMappingService fieldMappingService;
    private final ValueTransformerFactory transformerFactory;
    private final BatchEntityProcessor batchEntityProcessor;
    private final ProductRepository productRepository;

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
            // Устанавливаем статус "В обработке"
            operation.markAsProcessing();
            fileOperationRepository.save(operation);

            // Выполняем импорт
            ImportResult result = processImport(csvFile, mapping, client, operation);

            // Завершаем операцию
            operation.markAsCompleted(result.getTotalProcessed());
            operation.setProcessedRecords(result.getTotalProcessed());
            operation.setTotalRecords((int) result.getTotalRecords());

            fileOperationRepository.save(operation);

            log.info("CSV import completed successfully. Processed {} records",
                    result.getTotalProcessed());

            return CompletableFuture.completedFuture(operation);

        } catch (Exception e) {
            log.error("Error during CSV import for operation {}: {}",
                    operation.getId(), e.getMessage(), e);

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
        log.info("Using encoding: {}, delimiter: '{}'", mapping.getFileEncoding(), mapping.getCsvDelimiter());

        ImportResult result = new ImportResult();
        List<Map<String, String>> batchData = new ArrayList<>();

        // Безопасное создание кодировки
        Charset charset;
        try {
            charset = Charset.forName(mapping.getFileEncoding());
        } catch (Exception e) {
            log.error("Invalid encoding '{}', falling back to UTF-8", mapping.getFileEncoding());
            charset = StandardCharsets.UTF_8;
        }

        // Безопасное чтение файла с обработкой ошибок кодировки
        try (BufferedReader reader = createSafeBufferedReader(csvFile, charset)) {
            String line;
            String[] headers = null;
            int lineNumber = 0;
            int processedCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Пропускаем пустые строки
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Первая строка - заголовки
                if (headers == null) {
                    log.info("Header line: {}", line.substring(0, Math.min(line.length(), 100)));
                    headers = parseCsvLine(line, mapping);
                    result.setTotalRecords(estimateRecordCount(csvFile) - 1);
                    log.info("Found {} headers: {}", headers.length, Arrays.toString(headers));
                    log.info("Estimated {} total records", result.getTotalRecords());
                    continue;
                }

                try {
                    String[] values = parseCsvLine(line, mapping);
                    Map<String, String> rowData = createRowMap(headers, values);

                    // Логируем первые несколько строк для диагностики
                    if (lineNumber <= 5) {
                        log.info("Line {}: parsed {} values", lineNumber, values.length);
                        log.info("Row data keys: {}", rowData.keySet());
                        log.info("Sample row data: {}", rowData.entrySet().stream()
                                .limit(3)
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> e.getValue() != null ? e.getValue() : "NULL"
                                )));
                    }

                    batchData.add(rowData);

                    // Обрабатываем пакет данных
                    if (batchData.size() >= batchSize) {
                        BatchProcessResult batchResult = processBatch(batchData, mapping, client);
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
                BatchProcessResult batchResult = processBatch(batchData, mapping, client);
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
     * Создает BufferedReader с обработкой ошибок кодировки
     */
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

    /**
     * Обработка пакета данных с прямым связыванием через product_id
     */
    public BatchProcessResult processBatch(List<Map<String, String>> batchData,
                                           FieldMapping mapping, Client client) {

        log.debug("Processing batch of {} records", batchData.size());

        BatchProcessResult result = new BatchProcessResult();
        Map<String, List<ImportableEntity>> entitiesByType = new HashMap<>();

        for (Map<String, String> rowData : batchData) {
            try {
                // Извлекаем productId из исходных данных
                String productId = extractProductIdFromRow(rowData, mapping);

                // Применяем маппинг и создаем сущности
                Map<String, ImportableEntity> entities = fieldMappingService.applyMapping(mapping, rowData);

                // Устанавливаем общие поля и подготавливаем к связыванию
                for (Map.Entry<String, ImportableEntity> entry : entities.entrySet()) {
                    ImportableEntity entity = entry.getValue();
                    setCommonFields(entity, client, productId);

                    entitiesByType.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entity);
                }

                result.incrementProcessed();

            } catch (Exception e) {
                log.warn("Error processing row data: {}", e.getMessage());
                result.incrementFailed();
                result.addError(e.getMessage());
            }
        }

        // Для составного импорта нужно сначала обработать продукты, потом остальные
        if ("COMBINED".equals(mapping.getImportType())) {
            return processCombinedEntities(entitiesByType, mapping, client);
        } else {
            return processSingleEntity(entitiesByType, mapping);
        }
    }

    /**
     * Обработка составных сущностей: сначала продукты, потом связанные
     */
    private BatchProcessResult processCombinedEntities(Map<String, List<ImportableEntity>> entitiesByType,
                                                       FieldMapping mapping, Client client) {
        BatchProcessResult result = new BatchProcessResult();

        // 1. Сначала сохраняем продукты
        List<ImportableEntity> products = entitiesByType.get("PRODUCT");
        if (products != null && !products.isEmpty()) {
            try {
                DuplicateStrategy strategy = getDuplicateStrategy(mapping.getDuplicateStrategy());
                BatchSaveResult productResult = batchEntityProcessor.saveBatch(products, "PRODUCT", strategy);
                result.addSaveResult(productResult);
                log.debug("Saved {} products", products.size());
            } catch (Exception e) {
                log.error("Error saving products: {}", e.getMessage(), e);
                result.incrementFailed(products.size());
                result.addError("Ошибка сохранения продуктов: " + e.getMessage());
                return result; // Если продукты не сохранились, нет смысла сохранять связанные
            }
        }

        // 2. Теперь устанавливаем product_id для связанных сущностей и сохраняем их
        for (Map.Entry<String, List<ImportableEntity>> entry : entitiesByType.entrySet()) {
            String entityType = entry.getKey();
            List<ImportableEntity> entities = entry.getValue();

            if ("PRODUCT".equals(entityType)) {
                continue; // Продукты уже сохранили
            }

            try {
                // Устанавливаем product_id для связанных сущностей
                setProductReferences(entities, client.getId());

                DuplicateStrategy strategy = getDuplicateStrategy(mapping.getDuplicateStrategy());
                BatchSaveResult saveResult = batchEntityProcessor.saveBatch(entities, entityType, strategy);
                result.addSaveResult(saveResult);

            } catch (Exception e) {
                log.error("Error saving batch for entity type {}: {}", entityType, e.getMessage(), e);
                result.incrementFailed(entities.size());
                result.addError("Ошибка сохранения " + entityType + ": " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Обработка отдельной сущности
     */
    private BatchProcessResult processSingleEntity(Map<String, List<ImportableEntity>> entitiesByType,
                                                   FieldMapping mapping) {
        BatchProcessResult result = new BatchProcessResult();

        for (Map.Entry<String, List<ImportableEntity>> entry : entitiesByType.entrySet()) {
            String entityType = entry.getKey();
            List<ImportableEntity> entities = entry.getValue();

            try {
                DuplicateStrategy strategy = getDuplicateStrategy(mapping.getDuplicateStrategy());
                BatchSaveResult saveResult = batchEntityProcessor.saveBatch(entities, entityType, strategy);
                result.addSaveResult(saveResult);

            } catch (Exception e) {
                log.error("Error saving batch for entity type {}: {}", entityType, e.getMessage(), e);
                result.incrementFailed(entities.size());
                result.addError("Ошибка сохранения " + entityType + ": " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Извлекает productId из строки CSV данных
     */
    private String extractProductIdFromRow(Map<String, String> rowData, FieldMapping mapping) {
        // Находим поле, которое маппится на productId
        for (FieldMappingDetail detail : mapping.getDetails()) {
            if ("productId".equals(detail.getTargetField()) &&
                    ("PRODUCT".equals(detail.getTargetEntity()) || detail.getTargetEntity() == null)) {
                String productId = rowData.get(detail.getSourceField());
                if (productId != null && !productId.trim().isEmpty()) {
                    return productId.trim();
                }
            }
        }

        log.warn("ProductId not found in row data for mapping: {}", mapping.getName());
        return null;
    }

    /**
     * Установка общих полей
     */
    private void setCommonFields(ImportableEntity entity, Client client, String productId) {
        if (entity instanceof Product) {
            Product product = (Product) entity;
            product.setClientId(client.getId());
            product.setTransformerFactory(transformerFactory);

        } else if (entity instanceof Competitor) {
            Competitor competitor = (Competitor) entity;
            competitor.setClientId(client.getId());
            competitor.setTransformerFactory(transformerFactory);
            // Временно сохраняем productId - будет заменен на product_id позже
            competitor.setCompetitorAdditional2(productId);

        } else if (entity instanceof Region) {
            Region region = (Region) entity;
            region.setClientId(client.getId());
            region.setTransformerFactory(transformerFactory);
            // Временно сохраняем productId в дополнительном поле
            if (productId != null) {
                String currentAddress = region.getRegionAddress();
                if (currentAddress == null || currentAddress.isEmpty()) {
                    region.setRegionAddress("TEMP_PRODUCT_ID:" + productId);
                } else {
                    region.setRegionAddress(currentAddress + " [TEMP_PRODUCT_ID:" + productId + "]");
                }
            }
        }
    }

    /**
     * Устанавливает product_id для связанных сущностей
     */
    private void setProductReferences(List<ImportableEntity> entities, Long clientId) {
        if (entities.isEmpty()) {
            return;
        }

        // Собираем все productId для поиска
        Set<String> productIds = new HashSet<>();

        for (ImportableEntity entity : entities) {
            String productId = null;
            if (entity instanceof Competitor) {
                productId = ((Competitor) entity).getCompetitorAdditional2();
            } else if (entity instanceof Region) {
                String address = ((Region) entity).getRegionAddress();
                if (address != null && address.contains("TEMP_PRODUCT_ID:")) {
                    // Извлекаем productId из адреса
                    if (address.startsWith("TEMP_PRODUCT_ID:")) {
                        productId = address.substring("TEMP_PRODUCT_ID:".length());
                    } else {
                        int startIndex = address.indexOf("[TEMP_PRODUCT_ID:") + "[TEMP_PRODUCT_ID:".length();
                        int endIndex = address.indexOf("]", startIndex);
                        if (startIndex > 16 && endIndex > startIndex) {
                            productId = address.substring(startIndex, endIndex);
                        }
                    }
                }
            }

            if (productId != null && !productId.trim().isEmpty()) {
                productIds.add(productId.trim());
            }
        }

        if (productIds.isEmpty()) {
            log.warn("No productIds found for linking entities");
            return;
        }

        // Получаем соответствие productId -> database ID
        Map<String, Long> productIdToDbId = new HashMap<>();
        for (String productId : productIds) {
            productRepository.findByProductIdAndClientId(productId, clientId)
                    .ifPresent(product -> productIdToDbId.put(productId, product.getId()));
        }

        log.debug("Found {} existing products for linking from {} productIds",
                productIdToDbId.size(), productIds.size());

        // Устанавливаем связи
        for (ImportableEntity entity : entities) {
            if (entity instanceof Competitor) {
                Competitor competitor = (Competitor) entity;
                String productId = competitor.getCompetitorAdditional2();

                if (productId != null && productIdToDbId.containsKey(productId)) {
                    Product product = new Product();
                    product.setId(productIdToDbId.get(productId));
                    competitor.setProduct(product);
                    // Очищаем временное поле
                    competitor.setCompetitorAdditional2(null);
                    log.trace("Linked competitor to product: {}", productId);
                } else {
                    log.warn("Product not found for competitor productId: {}", productId);
                }

            } else if (entity instanceof Region) {
                Region region = (Region) entity;
                String address = region.getRegionAddress();
                String productId = null;
                String cleanAddress = null;

                if (address != null && address.contains("TEMP_PRODUCT_ID:")) {
                    if (address.startsWith("TEMP_PRODUCT_ID:")) {
                        productId = address.substring("TEMP_PRODUCT_ID:".length());
                        cleanAddress = null; // Адреса не было, оставляем пустым
                    } else {
                        int startIndex = address.indexOf("[TEMP_PRODUCT_ID:") + "[TEMP_PRODUCT_ID:".length();
                        int endIndex = address.indexOf("]", startIndex);
                        if (startIndex > 16 && endIndex > startIndex) {
                            productId = address.substring(startIndex, endIndex);
                            cleanAddress = address.substring(0, address.indexOf(" [TEMP_PRODUCT_ID:"));
                        }
                    }
                }

                if (productId != null && productIdToDbId.containsKey(productId)) {
                    Product product = new Product();
                    product.setId(productIdToDbId.get(productId));
                    region.setProduct(product);
                    // Восстанавливаем нормальный адрес
                    region.setRegionAddress(cleanAddress);
                    log.trace("Linked region to product: {}", productId);
                } else {
                    log.warn("Product not found for region productId: {}", productId);
                    // Очищаем технические данные даже если связь не установлена
                    if (address != null && address.contains("TEMP_PRODUCT_ID:")) {
                        if (address.startsWith("TEMP_PRODUCT_ID:")) {
                            region.setRegionAddress(null);
                        } else {
                            String cleanAddr = address.substring(0, address.indexOf(" [TEMP_PRODUCT_ID:"));
                            region.setRegionAddress(cleanAddr);
                        }
                    }
                }
            }
        }
    }

    /**
     * Парсинг строки CSV с учетом настроек маппинга
     */
    private String[] parseCsvLine(String line, FieldMapping mapping) {
        String delimiter = mapping.getCsvDelimiter();
        String quoteChar = mapping.getCsvQuoteChar();

        // Простой парсер CSV с поддержкой кавычек
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        char delimiterChar = delimiter.charAt(0);
        char quoteCharChar = quoteChar.charAt(0);

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escaped) {
                currentField.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (quoteCharChar == c) {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == c) {
                    // Удвоенная кавычка - добавляем одну
                    currentField.append(c);
                    i++; // Пропускаем следующую кавычку
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (delimiterChar == c && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }

        // Добавляем последнее поле
        fields.add(currentField.toString().trim());

        return fields.toArray(new String[0]);
    }

    /**
     * Создание Map из заголовков и значений
     */
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

    /**
     * Приблизительная оценка количества записей в файле
     */
    private long estimateRecordCount(Path csvFile) {
        try {
            long fileSize = Files.size(csvFile);
            // Используем простую оценку: файл размер / 100 символов на строку
            long estimatedLines = fileSize / 100;
            return Math.max(1, estimatedLines);
        } catch (IOException e) {
            log.warn("Could not estimate record count: {}", e.getMessage());
            return 1000; // Разумное значение по умолчанию
        }
    }

    /**
     * Обновление прогресса операции
     */
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
     * Преобразование строки стратегии в enum
     */
    private DuplicateStrategy getDuplicateStrategy(String strategyStr) {
        switch (strategyStr) {
            case "SKIP":
                return DuplicateStrategy.SKIP;
            case "OVERRIDE":
                return DuplicateStrategy.OVERRIDE;
            case "IGNORE":
                return DuplicateStrategy.IGNORE;
            default:
                return DuplicateStrategy.SKIP;
        }
    }

    /**
     * Результат импорта
     */
    public static class ImportResult {
        private int totalProcessed = 0;
        private long totalRecords = 0;
        private final List<String> errors = new ArrayList<>();
        private final List<BatchProcessResult> batchResults = new ArrayList<>();

        public int getTotalProcessed() { return totalProcessed; }
        public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }

        public long getTotalRecords() { return totalRecords; }
        public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }

        public List<String> getErrors() { return errors; }
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
        private int processed = 0;
        private int failed = 0;
        private final List<String> errors = new ArrayList<>();
        private final List<BatchSaveResult> saveResults = new ArrayList<>();

        public int getProcessed() { return processed; }
        public int getFailed() { return failed; }

        public void incrementProcessed() { processed++; }
        public void incrementFailed() { failed++; }
        public void incrementFailed(int count) { failed += count; }

        public void addError(String error) { errors.add(error); }
        public void addSaveResult(BatchSaveResult result) { saveResults.add(result); }
    }
}