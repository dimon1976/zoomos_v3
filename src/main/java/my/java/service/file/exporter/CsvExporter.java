// src/main/java/my/java/service/file/exporter/CsvExporter.java
package my.java.service.file.exporter;

import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.FileOperation;
import my.java.service.file.options.FileWritingOptions;
import my.java.util.PathResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class CsvExporter implements FileExporter {

    private final PathResolver pathResolver;
    private static final String[] SUPPORTED_TYPES = {"csv"};

    @Override
    public String[] getSupportedFileTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public boolean canExport(String fileType) {
        if (fileType == null) return false;

        String normalizedType = fileType.toLowerCase();
        for (String type : SUPPORTED_TYPES) {
            if (type.equals(normalizedType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Path exportData(
            List<Map<String, String>> data,
            List<String> fields,
            FileWritingOptions options,
            FileOperation operation) {

        try {
            // Создаем временный файл
            Path filePath = pathResolver.createTempFile("export", ".csv");

            // Получаем параметры CSV
            char delimiter = options.getDelimiter() != null ? options.getDelimiter() : ';';
            char quoteChar = options.getQuoteChar() != null ? options.getQuoteChar() : '"';

            // Начинаем этап экспорта
            operation.addStage("csv_export", "Экспорт данных в CSV");

            try (BufferedWriter writer = Files.newBufferedWriter(filePath, options.getCharset());
                 CSVWriter csvWriter = new CSVWriter(
                         writer,
                         delimiter,          // разделитель
                         quoteChar,          // символ кавычек
                         '\\',               // символ экранирования
                         System.lineSeparator()  // перевод строки
                 )) {

                // Запись заголовков, если нужно
                if (options.isIncludeHeader()) {
                    String[] headers = createHeaders(fields);
                    csvWriter.writeNext(headers);
                }

                // Запись данных
                int totalRecords = data.size();
                for (int i = 0; i < totalRecords; i++) {
                    Map<String, String> row = data.get(i);
                    String[] values = new String[fields.size()];

                    // Получаем значения полей
                    for (int j = 0; j < fields.size(); j++) {
                        values[j] = row.getOrDefault(fields.get(j), "");
                    }

                    csvWriter.writeNext(values);

                    // Обновляем прогресс
                    int progress = (int) (((double) (i + 1) / totalRecords) * 100);
                    operation.updateStageProgress("csv_export", progress);

                    // Обновляем прогресс всей операции
                    operation.setProcessingProgress(progress);
                }
            }

            operation.completeStage("csv_export");
            return filePath;
        } catch (Exception e) {
            log.error("Ошибка при экспорте в CSV: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при экспорте в CSV: " + e.getMessage(), e);
        }
    }

    private String[] createHeaders(List<String> fields) {
        String[] headers = new String[fields.size()];

        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);

            // Извлекаем имя поля без префикса сущности
            String header = field;
            int dotIndex = field.lastIndexOf('.');
            if (dotIndex > 0) {
                header = field.substring(dotIndex + 1);
            }

            // Преобразуем camelCase в нормальный текст
            StringBuilder formatted = new StringBuilder();
            for (int j = 0; j < header.length(); j++) {
                char c = header.charAt(j);
                if (j == 0) {
                    formatted.append(Character.toUpperCase(c));
                } else if (Character.isUpperCase(c)) {
                    formatted.append(' ').append(c);
                } else {
                    formatted.append(c);
                }
            }

            headers[i] = formatted.toString();
        }

        return headers;
    }
}