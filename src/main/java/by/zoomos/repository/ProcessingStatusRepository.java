package by.zoomos.repository;

import by.zoomos.model.entity.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Репозиторий для работы со статусами обработки
 */
@Repository
public interface ProcessingStatusRepository extends JpaRepository<ProcessingStatus, Long> {

    /**
     * Находит все статусы для клиента
     *
     * @param clientId идентификатор клиента
     * @param pageable параметры пагинации
     * @return страница статусов
     */
    Page<ProcessingStatus> findByClientIdOrderByCreatedAtDesc(Long clientId, Pageable pageable);

    /**
     * Находит активные процессы обработки
     *
     * @param clientId идентификатор клиента
     * @return список активных статусов
     */
    @Query("SELECT ps FROM ProcessingStatus ps WHERE ps.clientId = :clientId " +
            "AND ps.status IN ('PENDING', 'PROCESSING')")
    List<ProcessingStatus> findActiveProcesses(@Param("clientId") Long clientId);

    /**
     * Находит зависшие процессы
     *
     * @param threshold пороговое время
     * @return список зависших процессов
     */
    @Query("SELECT ps FROM ProcessingStatus ps WHERE ps.status = 'PROCESSING' " +
            "AND ps.updatedAt < :threshold")
    List<ProcessingStatus> findStuckProcesses(@Param("threshold") LocalDateTime threshold);

    /**
     * Подсчитывает количество обработанных файлов за период
     *
     * @param clientId идентификатор клиента
     * @param startDate начало периода
     * @param endDate конец периода
     * @return статистика обработки
     */
    @Query("SELECT new map(" +
            "COUNT(ps) as totalCount, " +
            "SUM(CASE WHEN ps.status = 'COMPLETED' THEN 1 ELSE 0 END) as successCount, " +
            "SUM(CASE WHEN ps.status = 'FAILED' THEN 1 ELSE 0 END) as failureCount, " +
            "SUM(ps.processedRecords) as totalRecords) " +
            "FROM ProcessingStatus ps " +
            "WHERE ps.clientId = :clientId " +
            "AND ps.createdAt BETWEEN :startDate AND :endDate")
    Map<String, Object> getProcessingStatistics(
            @Param("clientId") Long clientId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}