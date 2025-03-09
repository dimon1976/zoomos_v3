package my.java.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "file_operations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private OperationType operationType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "record_count")
    private Integer recordCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OperationStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "created_by")
    private String createdBy;

    // Enum для типа операции
    public enum OperationType {
        IMPORT, EXPORT, PROCESS
    }

    // Enum для статуса операции
    public enum OperationStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    // Метод для установки завершения операции
    public void markAsCompleted(int recordCount) {
        this.status = OperationStatus.COMPLETED;
        this.recordCount = recordCount;
        this.completedAt = ZonedDateTime.now();
    }

    // Метод для установки ошибки операции
    public void markAsFailed(String errorMessage) {
        this.status = OperationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = ZonedDateTime.now();
    }

    // Метод для изменения статуса на "в процессе"
    public void markAsProcessing() {
        this.status = OperationStatus.PROCESSING;
    }
}