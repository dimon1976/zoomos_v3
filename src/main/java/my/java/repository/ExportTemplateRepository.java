package my.java.repository;

import my.java.model.export.ExportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExportTemplateRepository extends JpaRepository<ExportTemplate, Long> {

    List<ExportTemplate> findByClientIdAndEntityTypeAndActiveTrue(Long clientId, String entityType);

    @Query("SELECT t FROM ExportTemplate t WHERE t.client.id = :clientId AND t.active = true " +
            "ORDER BY t.lastUsedAt DESC NULLS LAST")
    List<ExportTemplate> findRecentTemplatesByClientId(@Param("clientId") Long clientId);

    boolean existsByNameAndClientIdAndEntityType(String name, Long clientId, String entityType);
}