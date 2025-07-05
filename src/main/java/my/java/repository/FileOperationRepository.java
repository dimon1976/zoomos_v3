package my.java.repository;

import my.java.model.Client;
import my.java.model.FileOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Репозиторий для работы с файловыми операциями
 */
@Repository
public interface FileOperationRepository extends JpaRepository<FileOperation, Long> {

    /**
     * Найти все операции для клиента
     */
    List<FileOperation> findByClientOrderByStartedAtDesc(Client client);

    /**
     * Найти операции по статусу
     */
    List<FileOperation> findByStatus(FileOperation.OperationStatus status);

    /**
     * Найти операции клиента по статусу
     */
    List<FileOperation> findByClientAndStatus(Client client, FileOperation.OperationStatus status);

    /**
     * Найти последние операции клиента
     */
    @Query("SELECT fo FROM FileOperation fo WHERE fo.client = :client ORDER BY fo.startedAt DESC")
    List<FileOperation> findRecentOperationsByClient(@Param("client") Client client);

    /**
     * Найти операции по типу
     */
    List<FileOperation> findByClientAndOperationType(Client client, FileOperation.OperationType type);

    /**
     * Найти операции по шаблону маппинга
     */
    List<FileOperation> findByFieldMappingId(Long fieldMappingId);

    /**
     * Подсчитать количество операций для клиента
     */
    long countByClient(Client client);

    /**
     * Подсчитать количество успешных операций для клиента
     */
    long countByClientAndStatus(Client client, FileOperation.OperationStatus status);
}