// src/main/java/my/java/service/file/options/FileWritingOptions.java
package my.java.service.file.options;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileWritingOptions {
    // Общие параметры для всех типов файлов
    private String fileType = "CSV"; // CSV, EXCEL
    private boolean includeHeader = true;
    private int batchSize = 1000;

    // Параметры для CSV
    private Character delimiter = ',';
    private Character quoteChar = '"';
    private Charset charset = StandardCharsets.UTF_8;

    // Параметры для Excel
    private String sheetName = "Data";
    private boolean autoSizeColumns = true;

    // Дополнительные параметры
    private Map<String, String> additionalParams = new HashMap<>();

    /**
     * Создает экземпляр из Map параметров
     */
    public static FileWritingOptions fromMap(Map<String, String> params) {
        FileWritingOptions options = new FileWritingOptions();
        if (params == null) return options;

        // Общие параметры
        if (params.containsKey("fileType")) {
            options.setFileType(params.get("fileType").toUpperCase());
        }

        if (params.containsKey("includeHeader")) {
            options.setIncludeHeader(Boolean.parseBoolean(params.get("includeHeader")));
        }

        // Параметры CSV
        if (params.containsKey("delimiter") && !params.get("delimiter").isEmpty()) {
            options.setDelimiter(params.get("delimiter").charAt(0));
        }

        if (params.containsKey("quoteChar") && !params.get("quoteChar").isEmpty()) {
            options.setQuoteChar(params.get("quoteChar").charAt(0));
        }

        // Параметры Excel
        if (params.containsKey("sheetName")) {
            options.setSheetName(params.get("sheetName"));
        }

        if (params.containsKey("autoSizeColumns")) {
            options.setAutoSizeColumns(Boolean.parseBoolean(params.get("autoSizeColumns")));
        }

        // Дополнительные параметры
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!isStandardParam(entry.getKey())) {
                options.getAdditionalParams().put(entry.getKey(), entry.getValue());
            }
        }

        return options;
    }

    private static boolean isStandardParam(String key) {
        return key.equals("fileType") || key.equals("includeHeader") || key.equals("delimiter") ||
                key.equals("quoteChar") || key.equals("sheetName") || key.equals("autoSizeColumns");
    }
}