// src/main/java/my/java/repository/ExportTemplateRepository.java
package my.java.repository;

import my.java.model.ExportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExportTemplateRepository extends JpaRepository<ExportTemplate, Long> {

    List<ExportTemplate> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<ExportTemplate> findByClientIdAndEntityTypeOrderByCreatedAtDesc(Long clientId, String entityType);

    Optional<ExportTemplate> findByClientIdAndEntityTypeAndIsDefaultTrue(Long clientId, String entityType);
}