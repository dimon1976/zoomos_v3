package my.java.repository;

import my.java.model.FileOperation;
import my.java.model.FileOperation.OperationStatus;
import my.java.model.FileOperation.OperationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface FileOperationRepository extends JpaRepository<FileOperation, Long> {

    // Найти все операции для указанного клиента
    List<FileOperation> findByClientId(Long clientId);

    // Найти все операции для указанного клиента с пагинацией
    Page<FileOperation> findByClientId(Long clientId, Pageable pageable);

    // Найти операции по статусу
    List<FileOperation> findByStatus(OperationStatus status);

    // Найти операции для клиента по статусу
    List<FileOperation> findByClientIdAndStatus(Long clientId, OperationStatus status);

    // Найти операции для клиента по списку статусов
    List<FileOperation> findByClientIdAndStatusIn(Long clientId, Collection<OperationStatus> statuses);

    // Найти операции по типу
    List<FileOperation> findByOperationType(OperationType operationType);

    // Найти операции для клиента по типу операции
    List<FileOperation> findByClientIdAndOperationType(Long clientId, OperationType operationType);

    // Найти операции для клиента в указанном диапазоне дат
    List<FileOperation> findByClientIdAndStartedAtBetween(
            Long clientId, ZonedDateTime startDate, ZonedDateTime endDate);

    // Количество операций для клиента
    long countByClientId(Long clientId);

    // Количество успешных операций для клиента
    long countByClientIdAndStatus(Long clientId, OperationStatus status);

    // Статистика операций по статусам
    @Query("SELECT fo.status, COUNT(fo) FROM FileOperation fo GROUP BY fo.status")
    List<Object[]> countByStatusGroups();

    // Статистика операций по типам
    @Query("SELECT fo.operationType, COUNT(fo) FROM FileOperation fo GROUP BY fo.operationType")
    List<Object[]> countByOperationTypeGroups();

    // Найти последние операции клиента
    @Query("SELECT fo FROM FileOperation fo WHERE fo.client.id = :clientId ORDER BY fo.startedAt DESC")
    List<FileOperation> findLatestOperationsForClient(@Param("clientId") Long clientId, Pageable pageable);

    // Найти последние операции клиента с определенными статусами
    @Query("SELECT fo FROM FileOperation fo WHERE fo.client.id = :clientId AND fo.status IN :statuses ORDER BY fo.startedAt DESC")
    List<FileOperation> findLatestOperationsForClientWithStatus(
            @Param("clientId") Long clientId,
            @Param("statuses") Collection<OperationStatus> statuses,
            Pageable pageable);

    // Поиск операций по имени файла, содержащего указанную строку
    List<FileOperation> findByFileNameContainingIgnoreCase(String fileNamePart);
}