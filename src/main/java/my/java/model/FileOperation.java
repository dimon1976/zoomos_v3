// src/main/java/my/java/model/FileOperation.java (предполагаемый путь)
package my.java.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность для отслеживания операций с файлами
 */
@Entity
@Table(name = "file_operations")
@Data
@NoArgsConstructor
public class FileOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type")
    private OperationType operationType;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "source_file_path")
    private String sourceFilePath;

    @Column(name = "result_file_path")
    private String resultFilePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    private OperationStatus status;

    @Column(name = "started_at")
    private ZonedDateTime startedAt = ZonedDateTime.now();

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "processing_progress")
    private Integer processingProgress = 0;

    @Column(name = "total_records")
    private Integer totalRecords = 0;

    @Column(name = "processed_records")
    private Integer processedRecords = 0;

    @Column(name = "record_count")
    private Integer recordCount = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public enum OperationType {
        IMPORT, EXPORT
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "file_operation_stages",
            joinColumns = @JoinColumn(name = "operation_id"))
    @OrderColumn(name = "stage_order")
    private List<OperationStage> stages = new ArrayList<>();

    /**
     * Добавляет новый этап в операцию
     */
    public void addStage(String name, String description) {
        OperationStage stage = new OperationStage();
        stage.setName(name);
        stage.setDescription(description);
        stage.setProgress(0);
        stage.setStartedAt(ZonedDateTime.now());
        stage.setStatus(OperationStatus.PENDING);
        stages.add(stage);
    }

    /**
     * Обновляет прогресс этапа
     */
    public void updateStageProgress(String stageName, int progress) {
        stages.stream()
                .filter(s -> s.getName().equals(stageName))
                .findFirst()
                .ifPresent(stage -> {
                    stage.setProgress(progress);
                    if (stage.getStatus() == OperationStatus.PENDING) {
                        stage.setStatus(OperationStatus.PROCESSING);
                    }
                });
    }

    /**
     * Отмечает этап как завершенный
     */
    public void completeStage(String stageName) {
        stages.stream()
                .filter(s -> s.getName().equals(stageName))
                .findFirst()
                .ifPresent(stage -> {
                    stage.setProgress(100);
                    stage.setCompletedAt(ZonedDateTime.now());
                    stage.setStatus(OperationStatus.COMPLETED);
                });
    }

    /**
     * Отмечает этап как проваленный
     */
    public void failStage(String stageName, String errorMessage) {
        stages.stream()
                .filter(s -> s.getName().equals(stageName))
                .findFirst()
                .ifPresent(stage -> {
                    stage.setCompletedAt(ZonedDateTime.now());
                    stage.setStatus(OperationStatus.FAILED);
                });
    }
    @Embeddable
    @Data
    @NoArgsConstructor
    public static class OperationStage {
        private String name;
        private String description;
        private int progress;
        private ZonedDateTime startedAt;
        private ZonedDateTime completedAt;

        @Enumerated(EnumType.STRING)
        private OperationStatus status;
    }

    @Column(columnDefinition = "TEXT")
    private String additionalInfo;

    public enum OperationStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    /**
     * Обновляет статус на "В процессе"
     */
    public void markAsProcessing() {
        this.status = OperationStatus.PROCESSING;
        this.processingProgress = 0;
        this.startedAt = ZonedDateTime.now();
    }

    /**
     * Обновляет статус на "Успешно завершено"
     */
    public void markAsCompleted(int recordCount) {
        this.status = OperationStatus.COMPLETED;
        this.processingProgress = 100;
        this.recordCount = recordCount;
        this.completedAt = ZonedDateTime.now();
    }

    /**
     * Обновляет статус на "Ошибка"
     */
    public void markAsFailed(String errorMessage) {
        this.status = OperationStatus.FAILED;
        this.errorMessage = errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 997) + "..."
                : errorMessage;
        this.completedAt = ZonedDateTime.now();
    }

    /**
     * Проверяет, завершена ли операция
     */
    public boolean isCompleted() {
        return status == OperationStatus.COMPLETED || status == OperationStatus.FAILED;
    }
}