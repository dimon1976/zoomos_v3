// src/main/java/my/java/dto/FileOperationDto.java (предполагаемый путь)
package my.java.dto;

import lombok.Builder;
import lombok.Data;
import my.java.model.FileOperation;

import java.time.ZonedDateTime;

/**
 * DTO для передачи информации о файловых операциях
 */
@Data
@Builder
public class FileOperationDto {
    private Long id;
    private Long clientId;
    private FileOperation.OperationType operationType;
    private String fileName;
    private String fileType;
    private Integer recordCount;
    private FileOperation.OperationStatus status;
    private String errorMessage;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;

    /**
     * Создает DTO из сущности
     */
    public static FileOperationDto fromEntity(FileOperation operation) {
        if (operation == null) return null;

        return FileOperationDto.builder()
                .id(operation.getId())
                .clientId(operation.getClient() != null ? operation.getClient().getId() : null)
                .operationType(operation.getOperationType())
                .fileName(operation.getFileName())
                .fileType(operation.getFileType())
                .recordCount(operation.getRecordCount())
                .status(operation.getStatus())
                .errorMessage(operation.getErrorMessage())
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .build();
    }
}