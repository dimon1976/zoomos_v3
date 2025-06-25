package my.java.repository;

import my.java.model.FileOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileOperationRepository extends JpaRepository<FileOperation, Long> {

    /**
     * Найти операции клиента
     */
    Page<FileOperation> findByClientIdOrderByStartedAtDesc(Long clientId, Pageable pageable);

    /**
     * Найти операции по статусу
     */
    List<FileOperation> findByStatus(FileOperation.OperationStatus status);

    /**
     * Найти операции клиента по типу
     */
    @Query("SELECT f FROM FileOperation f WHERE f.client.id = :clientId " +
            "AND f.operationType = :type ORDER BY f.startedAt DESC")
    List<FileOperation> findByClientIdAndType(
            @Param("clientId") Long clientId,
            @Param("type") FileOperation.OperationType type
    );

    /**
     * Найти незавершенные операции
     */
    @Query("SELECT f FROM FileOperation f WHERE f.status IN ('PENDING', 'PROCESSING') " +
            "ORDER BY f.startedAt DESC")
    List<FileOperation> findIncompleteOperations();

    /**
     * Найти операции за период
     */
    @Query("SELECT f FROM FileOperation f WHERE f.startedAt BETWEEN :startDate AND :endDate")
    List<FileOperation> findByPeriod(
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate
    );

    /**
     * Получить статистику операций клиента
     */
    @Query("SELECT f.operationType, f.status, COUNT(f) FROM FileOperation f " +
            "WHERE f.client.id = :clientId GROUP BY f.operationType, f.status")
    List<Object[]> getClientOperationStats(@Param("clientId") Long clientId);

    /**
     * Найти последние операции
     */
    @Query("SELECT f FROM FileOperation f ORDER BY f.startedAt DESC")
    Page<FileOperation> findRecentOperations(Pageable pageable);

    /**
     * Найти операцию с клиентом
     */
    @Query("SELECT f FROM FileOperation f JOIN FETCH f.client WHERE f.id = :id")
    Optional<FileOperation> findByIdWithClient(@Param("id") Long id);
}