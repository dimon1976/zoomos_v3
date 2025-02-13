package by.zoomos.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Сущность для отслеживания статуса обработки файлов
 */
@Entity
@Table(name = "processing_status")
@Getter
@Setter
@NoArgsConstructor
public class ProcessingStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "processed_records")
    private Integer processedRecords;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum Status {
        PENDING,      // Ожидает обработки
        PROCESSING,   // В процессе обработки
        COMPLETED,    // Успешно завершено
        FAILED,       // Завершено с ошибкой
        CANCELLED     // Отменено
    }

    /**
     * Создает новый статус обработки
     */
    public static ProcessingStatus createNew(Long clientId, String fileName, Long fileSize) {
        ProcessingStatus status = new ProcessingStatus();
        status.setClientId(clientId);
        status.setFileName(fileName);
        status.setFileSize(fileSize);
        status.setStatus(Status.PENDING);
        status.setProcessedRecords(0);
        return status;
    }

    /**
     * Обновляет прогресс обработки
     */
    public void updateProgress(int processedRecords, int totalRecords) {
        this.processedRecords = processedRecords;
        this.totalRecords = totalRecords;
    }

    /**
     * Помечает обработку как завершенную
     */
    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Помечает обработку как проваленную
     */
    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Помечает обработку как отмененную
     */
    public void markCancelled(String reason) {
        this.status = Status.CANCELLED;
        this.errorMessage = reason;
        this.completedAt = LocalDateTime.now();
    }
}