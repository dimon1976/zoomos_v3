package my.java.repository;

import my.java.model.Client;
import my.java.model.FieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с шаблонами маппинга полей
 */
@Repository
public interface FieldMappingRepository extends JpaRepository<FieldMapping, Long> {

    /**
     * Найти все активные шаблоны для клиента
     */
    List<FieldMapping> findByClientAndIsActiveOrderByNameAsc(Client client, Boolean isActive);

    /**
     * Найти все шаблоны для клиента
     */
    List<FieldMapping> findByClientOrderByNameAsc(Client client);

    /**
     * Найти активные шаблоны для клиента по типу
     */
    List<FieldMapping> findByClientAndEntityTypeAndIsActive(Client client, String entityType, Boolean isActive);

    /**
     * Найти шаблон по имени для клиента
     */
    Optional<FieldMapping> findByClientAndNameIgnoreCase(Client client, String name);

    /**
     * Проверить существование шаблона с таким именем у клиента
     */
    boolean existsByClientAndNameIgnoreCase(Client client, String name);

    /**
     * Найти шаблон с деталями маппинга
     */
    @Query("SELECT fm FROM FieldMapping fm LEFT JOIN FETCH fm.details WHERE fm.id = :id")
    Optional<FieldMapping> findByIdWithDetails(@Param("id") Long id);

    /**
     * Найти все составные шаблоны для клиента
     */
    @Query("SELECT fm FROM FieldMapping fm WHERE fm.client = :client AND fm.importType = 'COMBINED' AND fm.isActive = true ORDER BY fm.name")
    List<FieldMapping> findCombinedMappingsForClient(@Param("client") Client client);

    /**
     * Найти все шаблоны для отдельных сущностей
     */
    @Query("SELECT fm FROM FieldMapping fm WHERE fm.client = :client AND fm.importType = 'SINGLE' AND fm.isActive = true ORDER BY fm.entityType, fm.name")
    List<FieldMapping> findSingleMappingsForClient(@Param("client") Client client);
}