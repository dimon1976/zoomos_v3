package my.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO для результатов импорта
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDto {
    private Long operationId;
    private String status;
    private Integer totalRows;
    private Integer processedRows;
    private Integer successRows;
    private Integer errorRows;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private List<String> errors;
    private Map<String, Integer> errorStatistics;
}
