// src/main/java/my/java/service/file/importer/CsvImportService.java
package my.java.service.file.importer;

import lombok.Getter;
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
import my.java.service.file.importer.strategy.DuplicateHandlingStrategy;
import my.java.service.file.importer.strategy.DuplicateHandlingStrategyFactory;
import my.java.service.file.importer.DuplicateStrategy;
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
    private final DuplicateHandlingStrategyFactory strategyFactory;

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
                                           FieldMapping mapping, Client client, FileOperation operation) {

        log.debug("Processing batch of {} records for {} import", batchData.size(), mapping.getImportType());

        BatchProcessResult result = new BatchProcessResult();

        // Используем новый класс для хранения связей
        EntityRelationshipHolder relationshipHolder = new EntityRelationshipHolder();

        // Этап 1: Парсинг данных и создание сущностей
        for (Map<String, String> rowData : batchData) {
            try {
                // Для SINGLE импорта используем индекс строки как уникальный идентификатор
                String rowIdentifier;
                if ("COMBINED".equals(mapping.getImportType())) {
                    // Для COMBINED импорта извлекаем productId
                    rowIdentifier = extractProductIdFromRow(rowData, mapping);
                } else {
                    // Для SINGLE импорта используем уникальный идентификатор строки
                    rowIdentifier = "row_" + result.getProcessed();
                }

                log.debug("Processing row with identifier: {}", rowIdentifier);

                // Начинаем новую строку
                relationshipHolder.startNewRow(rowIdentifier);

                // Применяем маппинг и создаем сущности
                Map<String, ImportableEntity> entities = fieldMappingService.applyMapping(mapping, rowData);

                // Распределяем сущности по типам с сохранением связей
                distributeEntitiesWithRelationships(entities, rowIdentifier, client, operation.getId(), relationshipHolder);

                // Завершаем текущую строку
                relationshipHolder.finishCurrentRow();

                result.incrementProcessed();
            } catch (Exception e) {
                log.warn("Error processing row data: {}", e.getMessage());
                result.incrementFailed();
                result.addError(e.getMessage());
            }
        }

        // Этап 2: Обработка сущностей согласно типу импорта и стратегии
        DuplicateHandlingStrategy strategy = strategyFactory.getStrategy(
                DuplicateStrategy.valueOf(mapping.getDuplicateStrategy()));

        if ("COMBINED".equals(mapping.getImportType())) {
            return processCombinedEntitiesWithStrategy(relationshipHolder, strategy, client.getId());
        } else {
            return processSingleEntityWithStrategy(relationshipHolder, strategy, mapping.getEntityType(), client.getId());
        }
    }

    private BatchProcessResult processSingleEntityWithStrategy(EntityRelationshipHolder holder,
                                                               DuplicateHandlingStrategy strategy,
                                                               String entityType,
                                                               Long clientId) {
        BatchProcessResult result = new BatchProcessResult();

        try {
            log.debug("Processing SINGLE import for entity type: {}", entityType);

            List<ImportableEntity> entities = new ArrayList<>();

            // Для SINGLE импорта собираем все сущности нужного типа
            switch (entityType) {
                case "PRODUCT" -> {
                    entities.addAll(holder.getAllProducts());
                    log.debug("Found {} products for SINGLE import", entities.size());
                }
                case "COMPETITOR" -> {
                    // Для SINGLE импорта конкурентов они не связаны с продуктами
                    for (EntityRelationshipHolder.ImportRow row : holder.getAllRows()) {
                        entities.addAll(row.getCompetitors());
                    }
                    log.debug("Found {} competitors for SINGLE import", entities.size());
                }
                case "REGION" -> {
                    // Для SINGLE импорта регионов они не связаны с продуктами
                    for (EntityRelationshipHolder.ImportRow row : holder.getAllRows()) {
                        entities.addAll(row.getRegions());
                    }
                    log.debug("Found {} regions for SINGLE import", entities.size());
                }
                default -> {
                    log.error("Unknown entity type for SINGLE import: {}", entityType);
                    result.addError("Неизвестный тип сущности: " + entityType);
                    return result;
                }
            }

            if (!entities.isEmpty()) {
                log.info("Processing {} entities of type {} with strategy {}",
                        entities.size(), entityType, strategy.getType());

                BatchSaveResult saveResult = strategy.process(entities, entityType, clientId, new HashMap<>());
                result.addSaveResult(saveResult);

                log.info("SINGLE import result: saved={}, failed={}",
                        saveResult.getSaved(), saveResult.getFailed());
            } else {
                log.warn("No entities found for SINGLE import of type {}", entityType);
                result.addError("Не найдено данных для импорта");
            }

        } catch (Exception e) {
            log.error("Error in single entity processing: {}", e.getMessage(), e);
            result.incrementFailed(holder.getTotalEntitiesCount());
            result.addError("Ошибка обработки импорта: " + e.getMessage());
        }

        return result;
    }

    /**
     * Определение типа сущности для SINGLE импорта
     */
    private String determineEntityTypeForSingleImport(EntityRelationshipHolder holder) {
        if (!holder.getAllProducts().isEmpty()) {
            return "PRODUCT";
        } else if (!holder.getCompetitorsByProductId().isEmpty()) {
            return "COMPETITOR";
        } else if (!holder.getRegionsByProductId().isEmpty()) {
            return "REGION";
        }
        return null;
    }


    /**
     * Обработка COMBINED импорта с использованием стратегии
     */
    private BatchProcessResult processCombinedEntitiesWithStrategy(EntityRelationshipHolder holder,
                                                                   DuplicateHandlingStrategy strategy,
                                                                   Long clientId) {
        BatchProcessResult result = new BatchProcessResult();

        try {
            // Подготавливаем данные в зависимости от стратегии
            List<ImportableEntity> products;
            Map<String, List<ImportableEntity>> relatedEntities = new HashMap<>();
            relatedEntities.put("COMPETITOR", new ArrayList<>());
            relatedEntities.put("REGION", new ArrayList<>());

            switch (strategy.getType()) {
                case IGNORE -> {
                    // IGNORE - берем все продукты, включая дубликаты
                    products = new ArrayList<>(holder.getAllProducts());

                    // Собираем все связанные сущности из всех строк
                    for (EntityRelationshipHolder.ImportRow row : holder.getAllRows()) {
                        relatedEntities.get("COMPETITOR").addAll(row.getCompetitors());
                        relatedEntities.get("REGION").addAll(row.getRegions());
                    }
                }

                case SKIP -> {
                    // SKIP - берем только уникальные продукты
                    products = new ArrayList<>(holder.getUniqueProducts());

                    // Собираем связанные сущности только для первого вхождения каждого productId
                    Set<String> processedProductIds = new HashSet<>();
                    for (EntityRelationshipHolder.ImportRow row : holder.getAllRows()) {
                        if (row.getProductId() != null && !processedProductIds.contains(row.getProductId())) {
                            processedProductIds.add(row.getProductId());
                            relatedEntities.get("COMPETITOR").addAll(row.getCompetitors());
                            relatedEntities.get("REGION").addAll(row.getRegions());
                        }
                    }
                }

                case OVERRIDE -> {
                    // OVERRIDE - берем последнее вхождение каждого productId
                    Map<String, EntityRelationshipHolder.ImportRow> lastRowByProductId = new HashMap<>();
                    for (EntityRelationshipHolder.ImportRow row : holder.getAllRows()) {
                        if (row.getProductId() != null) {
                            lastRowByProductId.put(row.getProductId(), row);
                        }
                    }

                    products = new ArrayList<>();
                    for (EntityRelationshipHolder.ImportRow row : lastRowByProductId.values()) {
                        products.add(row.getProduct());
                        relatedEntities.get("COMPETITOR").addAll(row.getCompetitors());
                        relatedEntities.get("REGION").addAll(row.getRegions());
                    }
                }

                default -> throw new IllegalArgumentException("Unknown strategy: " + strategy.getType());
            }

            // Обрабатываем через стратегию с передачей holder
            BatchSaveResult saveResult = strategy.processCombined(products, relatedEntities, clientId, holder);
            result.addSaveResult(saveResult);

        } catch (Exception e) {
            log.error("Error in combined processing: {}", e.getMessage(), e);
            result.incrementFailed(holder.getTotalEntitiesCount());
            result.addError("Ошибка обработки составного импорта: " + e.getMessage());
        }

        return result;
    }


    /**
     * Распределение сущностей с сохранением связей через EntityRelationshipHolder
     */
    private void distributeEntitiesWithRelationships(Map<String, ImportableEntity> entities,
                                                     String rowIdentifier,
                                                     Client client,
                                                     Long operationId,
                                                     EntityRelationshipHolder holder) {

        log.debug("Distributing {} entities for rowIdentifier: {}", entities.size(), rowIdentifier);

        for (Map.Entry<String, ImportableEntity> entry : entities.entrySet()) {
            ImportableEntity entity = entry.getValue();
            String entityType = entry.getKey();

            log.debug("Processing entity type: {}, rowIdentifier: {}", entityType, rowIdentifier);

            // Устанавливаем общие поля
            setCommonFields(entity, client.getId(), operationId);

            // Добавляем в holder с сохранением связей
            switch (entityType) {
                case "PRODUCT" -> {
                    Product product = (Product) entity;
                    log.debug("Adding product to holder: productId={}, productName={}",
                            product.getProductId(), product.getProductName());
                    holder.addProduct(product);
                }
                case "COMPETITOR" -> {
                    Competitor competitor = (Competitor) entity;
                    log.debug("Adding competitor to holder: rowIdentifier={}, competitorName={}",
                            rowIdentifier, competitor.getCompetitorName());
                    // Для SINGLE импорта конкурентов productId может быть null
                    holder.addCompetitor(rowIdentifier, competitor);
                }
                case "REGION" -> {
                    Region region = (Region) entity;
                    log.debug("Adding region to holder: rowIdentifier={}, regionName={}",
                            rowIdentifier, region.getRegion());
                    // Для SINGLE импорта регионов productId может быть null
                    holder.addRegion(rowIdentifier, region);
                }
                default -> log.warn("Unknown entity type: {}", entityType);
            }
        }
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
     * Установка общих полей включая operation_id
     */
    private void setCommonFields(ImportableEntity entity, Long clientId, Long operationId) {
        entity.setClientId(clientId);
        entity.setTransformerFactory(transformerFactory);

        if (entity instanceof Product product) {
            product.setOperationId(operationId);
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

        public void incrementFailed(int count) {
            failed += count;
        }

        public void addError(String error) {
            errors.add(error);
        }

        public void addSaveResult(BatchSaveResult result) {
            saveResults.add(result);
        }
    }
}