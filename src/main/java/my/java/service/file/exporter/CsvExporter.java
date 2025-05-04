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
import java.util.ArrayList;
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
            char delimiter = options.getDelimiter() != null ? options.getDelimiter() : ',';
            char quoteChar = options.getQuoteChar() != null ? options.getQuoteChar() : '"';

            // Начинаем этап экспорта
            operation.addStage("csv_export", "Экспорт данных в CSV");

            // Подготавливаем данные для экспорта
            List<String[]> allLines = prepareDataWithHeaders(data, fields, options);

            try (BufferedWriter writer = Files.newBufferedWriter(filePath, options.getCharset());
                 CSVWriter csvWriter = new CSVWriter(
                         writer,
                         delimiter,          // разделитель
                         quoteChar,          // символ кавычек
                         CSVWriter.DEFAULT_ESCAPE_CHARACTER,  // символ экранирования (в OpenCSV используется для CSV по формату RFC4180)
                         System.lineSeparator()  // перевод строки
                 )) {

                // Записываем все данные, заключая их в кавычки
                // Второй параметр true гарантирует, что все поля будут в кавычках
                // и кавычки внутри значений будут корректно экранированы (удвоены)
                csvWriter.writeAll(allLines, true);

                // Обновляем прогресс
                operation.updateStageProgress("csv_export", 100);
                operation.setProcessingProgress(100);
            }

            operation.completeStage("csv_export");
            return filePath;
        } catch (Exception e) {
            log.error("Ошибка при экспорте в CSV: {}", e.getMessage(), e);
            throw new FileOperationException("Ошибка при экспорте в CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Подготавливает данные для экспорта вместе с заголовками
     */
    private List<String[]> prepareDataWithHeaders(
            List<Map<String, String>> data,
            List<String> fields,
            FileWritingOptions options) {

        List<String[]> result = new ArrayList<>(data.size() + 1);

        // Добавляем заголовки, если нужно
        if (options.isIncludeHeader()) {
            String[] headers = createHeaders(fields, options);
            result.add(headers);
        }

        // Добавляем данные
        for (Map<String, String> row : data) {
            String[] values = new String[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                values[i] = row.getOrDefault(fields.get(i), "");
            }
            result.add(values);
        }

        return result;
    }

    /**
     * Создает заголовки для CSV-файла с учетом пользовательских настроек
     */
    private String[] createHeaders(List<String> fields, FileWritingOptions options) {
        String[] headers = new String[fields.size()];

        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);

            // Проверяем, есть ли пользовательский заголовок в параметрах
            String customHeader = options.getAdditionalParams().get("header_" + field.replace(".", "_"));

            if (customHeader != null && !customHeader.isEmpty()) {
                headers[i] = customHeader;
            } else {
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
        }

        return headers;
    }
}