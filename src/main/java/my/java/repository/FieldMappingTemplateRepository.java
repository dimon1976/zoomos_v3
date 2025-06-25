package my.java.repository;

import my.java.model.FieldMappingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldMappingTemplateRepository extends JpaRepository<FieldMappingTemplate, Long> {

    /**
     * Найти все активные шаблоны для клиента
     */
    @Query("SELECT t FROM FieldMappingTemplate t WHERE t.client.id = :clientId AND t.isActive = true")
    List<FieldMappingTemplate> findActiveByClientId(@Param("clientId") Long clientId);

    /**
     * Найти шаблоны по типу сущности
     */
    @Query("SELECT t FROM FieldMappingTemplate t WHERE t.entityType = :entityType AND t.isActive = true")
    List<FieldMappingTemplate> findActiveByEntityType(@Param("entityType") String entityType);

    /**
     * Найти шаблоны для клиента и типа сущности
     */
    @Query("SELECT t FROM FieldMappingTemplate t WHERE t.client.id = :clientId " +
            "AND t.entityType = :entityType AND t.isActive = true")
    List<FieldMappingTemplate> findActiveByClientIdAndEntityType(
            @Param("clientId") Long clientId,
            @Param("entityType") String entityType
    );

    /**
     * Найти шаблон по умолчанию для типа сущности
     */
    @Query("SELECT t FROM FieldMappingTemplate t WHERE t.entityType = :entityType " +
            "AND t.isDefault = true AND t.isActive = true")
    Optional<FieldMappingTemplate> findDefaultByEntityType(@Param("entityType") String entityType);

    /**
     * Найти шаблон с правилами маппинга
     */
    @Query("SELECT t FROM FieldMappingTemplate t LEFT JOIN FETCH t.rules WHERE t.id = :id")
    Optional<FieldMappingTemplate> findByIdWithRules(@Param("id") Long id);

    /**
     * Проверить существование шаблона с таким именем у клиента
     */
    boolean existsByNameAndClientId(String name, Long clientId);
}