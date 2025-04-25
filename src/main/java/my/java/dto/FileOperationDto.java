// src/main/java/my/java/dto/FileOperationDto.java (предполагаемый путь)
package my.java.dto;

import lombok.Builder;
import lombok.Data;
import my.java.model.FileOperation;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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


    // Вспомогательные методы для форматирования дат
    public String getFormattedStartedAt() {
        return formatDateTime(startedAt);
    }

    public String getFormattedCompletedAt() {
        return formatDateTime(completedAt);
    }

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


    // Получение типа операции в виде текста для отображения
    public String getOperationTypeDisplay() {
        if (operationType == null) {
            return "";
        }

        switch (operationType) {
            case IMPORT:
                return "Импорт";
            case EXPORT:
                return "Экспорт";
            default:
                return operationType.toString();
        }
    }

    // Получение статуса операции в виде текста для отображения
    public String getStatusDisplay() {
        if (status == null) {
            return "";
        }

        switch (status) {
            case PENDING:
                return "Ожидает";
            case PROCESSING:
                return "Выполняется";
            case COMPLETED:
                return "Завершено";
            case FAILED:
                return "Ошибка";
            default:
                return status.toString();
        }
    }

    // Получение CSS-класса для статуса (для цветового отображения)
    public String getStatusClass() {
        if (status == null) {
            return "status-unknown";
        }

        switch (status) {
            case PENDING:
                return "status-pending";
            case PROCESSING:
                return "status-processing";
            case COMPLETED:
                return "status-success";
            case FAILED:
                return "status-error";
            default:
                return "status-unknown";
        }
    }

    // Вспомогательный метод для форматирования даты и времени
    private String formatDateTime(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }


}