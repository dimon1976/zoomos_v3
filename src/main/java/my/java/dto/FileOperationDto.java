package my.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import my.java.model.FileOperation.OperationStatus;
import my.java.model.FileOperation.OperationType;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOperationDto {

    private Long id;
    private Long clientId;
    private String clientName;
    private OperationType operationType;
    private String fileName;
    private String fileType;
    private Integer recordCount;
    private OperationStatus status;
    private String errorMessage;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private String createdBy;
    private Integer processingProgress;
    private Integer processedRecords;
    private Integer totalRecords;
    private Integer batchSize;
    private String processingStrategy;
    private String errorHandling;
    private String duplicateHandling;
    private String entityType;

    // Вспомогательные методы для форматирования дат
    public String getFormattedStartedAt() {
        return formatDateTime(startedAt);
    }

    public String getFormattedCompletedAt() {
        return formatDateTime(completedAt);
    }

    // Получение продолжительности операции (в секундах или "В процессе"/"Не завершено")
    public String getDuration() {
        if (startedAt == null) {
            return "Н/Д";
        }

        if (completedAt == null) {
            if (status == OperationStatus.PROCESSING || status == OperationStatus.PENDING) {
                return "В процессе";
            }
            return "Не завершено";
        }

        long durationSeconds = completedAt.toEpochSecond() - startedAt.toEpochSecond();

        // Форматирование длительности
        if (durationSeconds < 60) {
            return durationSeconds + " сек";
        } else if (durationSeconds < 3600) {
            long minutes = durationSeconds / 60;
            long seconds = durationSeconds % 60;
            return String.format("%d мин %d сек", minutes, seconds);
        } else {
            long hours = durationSeconds / 3600;
            long minutes = (durationSeconds % 3600) / 60;
            return String.format("%d ч %d мин", hours, minutes);
        }
    }

    // Проверка завершения операции
    public Boolean isCompleted(OperationStatus status) {
        if (OperationStatus.COMPLETED.equals(status)) {
            return true;
        }
        return false;
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
            case PROCESS:
                return "Обработка";
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