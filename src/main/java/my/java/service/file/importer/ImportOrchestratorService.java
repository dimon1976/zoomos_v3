// src/main/java/my/java/service/file/importer/ImportOrchestratorService.java
package my.java.service.file.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FieldMapping;
import my.java.model.FileOperation;
import my.java.repository.FieldMappingRepository;
import my.java.repository.FileOperationRepository;
import my.java.service.client.ClientService;
import my.java.service.file.analyzer.CsvAnalysisResult;
import my.java.service.file.analyzer.CsvFileAnalyzer;
import my.java.util.PathResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Основной сервис для координации процесса импорта файлов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImportOrchestratorService {

    private final CsvImportService csvImportService;
    private final CsvFileAnalyzer csvFileAnalyzer;
    private final ClientService clientService;
    private final FieldMappingRepository fieldMappingRepository;
    private final FileOperationRepository fileOperationRepository;
    private final PathResolver pathResolver;

    /**
     * Запуск импорта файла
     */
    public FileOperation startImport(Long clientId, MultipartFile file, Long mappingId) {
        log.info("=== Starting import process ===");
        log.info("Client ID: {}", clientId);
        log.info("Mapping ID: {}", mappingId);
        log.info("Original filename: {}", file.getOriginalFilename());
        log.info("File size: {} bytes ({})", file.getSize(), formatFileSize(file.getSize()));
        log.info("Content type: {}", file.getContentType());

        try {
            // Получаем клиента
            Client client = clientService.findClientEntityById(clientId)
                    .orElseThrow(() -> new FileOperationException("Клиент не найден"));
            log.info("Found client: {}", client.getName());

            // Получаем шаблон маппинга
            FieldMapping mapping = fieldMappingRepository.findByIdWithDetails(mappingId)
                    .orElseThrow(() -> new FileOperationException("Шаблон маппинга не найден"));
            log.info("Found mapping: '{}' with {} details", mapping.getName(), mapping.getDetails().size());

            // Валидация файла
            log.info("Validating uploaded file...");
            validateFile(file, mapping);
            log.info("File validation passed");

            // Сохраняем файл во временную директорию
            log.info("Saving file to temporary directory...");
            Path tempFile = pathResolver.saveToTempFile(file, "import_" + clientId);
            log.info("File saved to: {}", tempFile.toAbsolutePath());

            // Анализируем CSV файл
            CsvAnalysisResult analysisResult = null;
            if (file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                log.info("Analyzing CSV file structure...");
                analysisResult = csvFileAnalyzer.analyzeFile(tempFile);
                log.info("CSV analysis completed - detected encoding: {}, delimiter: '{}', {} headers",
                        analysisResult.getEncoding(), analysisResult.getDelimiter(),
                        analysisResult.getHeaders().size());

                validateCsvStructure(analysisResult, mapping);
            } else {
                log.info("Excel file detected, skipping CSV analysis");
            }

            // Создаем операцию
            log.info("Creating file operation record...");
            FileOperation operation = createFileOperation(client, file, mapping, tempFile, analysisResult);
            operation = fileOperationRepository.save(operation);
            log.info("Created file operation with ID: {}", operation.getId());

            // Запускаем асинхронную обработку
            log.info("Starting async import processing...");
            CompletableFuture<FileOperation> future = csvImportService.importCsvAsync(
                    tempFile, mapping, client, operation);

            // Добавляем обработчик завершения для очистки временных файлов
            FileOperation finalOperation = operation;
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Import failed for operation {}: {}", finalOperation.getId(), throwable.getMessage());
                } else {
                    log.info("Import completed successfully for operation {}", finalOperation.getId());
                }

                // Очищаем временный файл
                try {
                    pathResolver.deleteFile(tempFile);
                    log.debug("Cleaned up temporary file: {}", tempFile);
                } catch (Exception e) {
                    log.warn("Failed to delete temp file {}: {}", tempFile, e.getMessage());
                }
            });

            log.info("=== Import process initiated successfully ===");
            return operation;

        } catch (Exception e) {
            log.error("=== Error starting import ===");
            log.error("Error details: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка запуска импорта: " + e.getMessage(), e);
        }
    }

    /**
     * Форматирование размера файла для логов
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Валидация загруженного файла
     */
    private void validateFile(MultipartFile file, FieldMapping mapping) {
        // Проверка наличия файла
        if (file.isEmpty()) {
            throw new FileOperationException("Файл пустой");
        }

        // Проверка имени файла
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new FileOperationException("Имя файла не указано");
        }

        // Проверка формата файла
        if (!filename.toLowerCase().endsWith(".csv") && !filename.toLowerCase().endsWith(".xlsx")) {
            throw new FileOperationException("Поддерживаются только файлы формата CSV и XLSX");
        }

        // Проверка размера файла (600 МБ максимум)
        if (file.getSize() > 600 * 1024 * 1024) {
            throw new FileOperationException("Размер файла не должен превышать 600 МБ");
        }

        // Проверка активности шаблона
        if (!mapping.getIsActive()) {
            throw new FileOperationException("Выбранный шаблон маппинга неактивен");
        }

        // Проверка наличия деталей маппинга
        if (mapping.getDetails().isEmpty()) {
            throw new FileOperationException("Шаблон маппинга не содержит сопоставлений полей");
        }
    }

    /**
     * Валидация структуры CSV файла
     */
    private void validateCsvStructure(CsvAnalysisResult analysisResult, FieldMapping mapping) {
        log.info("Validating CSV structure");
        log.info("Analysis result - encoding: {}, delimiter: '{}', headers: {}",
                analysisResult.getEncoding(), analysisResult.getDelimiter(), analysisResult.isHasHeaders());
        log.info("Mapping expects - encoding: {}, delimiter: '{}'",
                mapping.getFileEncoding(), mapping.getCsvDelimiter());

        // Проверка кодировки
        if (!analysisResult.getEncoding().equals(mapping.getFileEncoding())) {
            log.warn("File encoding '{}' differs from mapping encoding '{}'",
                    analysisResult.getEncoding(), mapping.getFileEncoding());
            log.warn("This may cause character encoding issues during import");
        }

        // Проверка разделителя
        String expectedDelimiter = mapping.getCsvDelimiter();
        char actualDelimiter = analysisResult.getDelimiter();
        if (!expectedDelimiter.equals(String.valueOf(actualDelimiter))) {
            log.warn("File delimiter '{}' differs from mapping delimiter '{}'",
                    actualDelimiter, expectedDelimiter);
            log.warn("This may cause incorrect field parsing");
        }

        // Проверка наличия заголовков
        if (!analysisResult.isHasHeaders()) {
            log.error("CSV file validation failed: no headers detected");
            throw new FileOperationException("CSV файл должен содержать заголовки в первой строке");
        }

        // Проверка минимального количества столбцов
        if (analysisResult.getHeaders().size() < mapping.getDetails().size()) {
            log.warn("File has {} headers but mapping expects {} fields",
                    analysisResult.getHeaders().size(), mapping.getDetails().size());
            log.debug("File headers: {}", analysisResult.getHeaders());
            log.debug("Expected mapping fields: {}",
                    mapping.getDetails().stream()
                            .map(detail -> detail.getSourceField())
                            .collect(Collectors.toList()));
        }

        // Проверяем, есть ли обязательные поля в заголовках файла
        List<String> fileHeaders = analysisResult.getHeaders();
        List<String> missingRequiredFields = mapping.getDetails().stream()
                .filter(detail -> detail.getRequired())
                .map(detail -> detail.getSourceField())
                .filter(field -> !fileHeaders.contains(field))
                .collect(Collectors.toList());

        if (!missingRequiredFields.isEmpty()) {
            log.error("Missing required fields in CSV: {}", missingRequiredFields);
            throw new FileOperationException("В файле отсутствуют обязательные поля: " +
                    String.join(", ", missingRequiredFields));
        }

        log.info("CSV structure validation completed successfully");
    }

    /**
     * Создание операции импорта файла
     */
    private FileOperation createFileOperation(Client client, MultipartFile file,
                                              FieldMapping mapping, Path tempFile,
                                              CsvAnalysisResult analysisResult) {

        String fileHash = calculateFileHash(file);

        return FileOperation.builder()
                .client(client)
                .operationType(FileOperation.OperationType.IMPORT)
                .fileName(file.getOriginalFilename())
                .fileType(getFileType(file.getOriginalFilename()))
                .status(FileOperation.OperationStatus.PENDING)
                .sourceFilePath(tempFile.toString())
                .fileSize(file.getSize())
                .fieldMappingId(mapping.getId())
                .processingProgress(0)
                .processedRecords(0)
                .fileHash(fileHash)
                .totalRecords(analysisResult != null ? (int) analysisResult.getEstimatedLines() : 0)
                .processingParams(buildProcessingParams(mapping, analysisResult))
                .build();
    }

    /**
     * Определение типа файла
     */
    private String getFileType(String filename) {
        if (filename.toLowerCase().endsWith(".csv")) {
            return "CSV";
        } else if (filename.toLowerCase().endsWith(".xlsx")) {
            return "XLSX";
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Расчет хеша файла для проверки целостности
     */
    private String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(file.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            log.warn("Failed to calculate file hash: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Построение параметров обработки
     */
    private String buildProcessingParams(FieldMapping mapping, CsvAnalysisResult analysisResult) {
        StringBuilder params = new StringBuilder();

        params.append("mapping_id=").append(mapping.getId());
        params.append(";encoding=").append(mapping.getFileEncoding());
        params.append(";delimiter=").append(mapping.getCsvDelimiter());
        params.append(";quote_char=").append(mapping.getCsvQuoteChar());
        params.append(";duplicate_strategy=").append(mapping.getDuplicateStrategy());

        if (analysisResult != null) {
            params.append(";detected_encoding=").append(analysisResult.getEncoding());
            params.append(";detected_delimiter=").append(analysisResult.getDelimiter());
            params.append(";estimated_lines=").append(analysisResult.getEstimatedLines());
            params.append(";file_size=").append(analysisResult.getFileSize());
        }

        return params.toString();
    }

    /**
     * Получение статуса операции
     */
    public FileOperation getOperationStatus(Long operationId) {
        return fileOperationRepository.findById(operationId)
                .orElseThrow(() -> new FileOperationException("Операция не найдена"));
    }

    /**
     * Отмена операции (если возможно)
     */
    public boolean cancelOperation(Long operationId) {
        FileOperation operation = getOperationStatus(operationId);

        if (operation.getStatus() == FileOperation.OperationStatus.PENDING) {
            operation.markAsFailed("Операция отменена пользователем");
            fileOperationRepository.save(operation);
            return true;
        }

        return false; // Нельзя отменить операцию в процессе
    }
}