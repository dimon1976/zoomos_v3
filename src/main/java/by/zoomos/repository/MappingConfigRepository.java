package by.zoomos.repository;

import by.zoomos.model.entity.MappingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с конфигурациями маппинга
 */
@Repository
public interface MappingConfigRepository extends JpaRepository<MappingConfig, Long> {

    /**
     * Находит все активные конфигурации для клиента
     */
    List<MappingConfig> findByClientIdAndActiveTrue(Long clientId);

    /**
     * Находит конфигурацию по умолчанию для клиента
     */
    Optional<MappingConfig> findByClientIdAndIsDefaultTrue(Long clientId);

    /**
     * Находит конфигурации для клиента по типу файла
     */
    List<MappingConfig> findByClientIdAndFileTypeAndActiveTrue(
            Long clientId, MappingConfig.FileType fileType);

    /**
     * Проверяет существование конфигурации по умолчанию
     */
    boolean existsByClientIdAndIsDefaultTrue(Long clientId);

    /**
     * Снимает флаг "по умолчанию" со всех конфигураций клиента
     */
    @Query("UPDATE MappingConfig m SET m.isDefault = false WHERE m.clientId = :clientId")
    void clearDefaultForClient(@Param("clientId") Long clientId);
}