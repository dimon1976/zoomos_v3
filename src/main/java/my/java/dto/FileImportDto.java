package my.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO для параметров импорта файла
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileImportDto {

    private Long clientId;
    private Long mappingTemplateId;
    private String encoding;
    private String delimiter;
    private String quoteChar;
    private Boolean hasHeader;
    private String entityType;

    // Дополнительные параметры
    private Boolean skipEmptyLines;
    private Boolean trimValues;
    private Integer batchSize;
}

/**
 * DTO для предварительного просмотра файла
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class FilePreviewDto {
    private String fileName;
    private Long fileSize;
    private String encoding;
    private String delimiter;
    private String quoteChar;
    private Boolean hasHeader;
    private Integer columnCount;
    private List<String> headers;
    private List<Map<String, String>> sampleData;
    private Integer totalRows;
}

