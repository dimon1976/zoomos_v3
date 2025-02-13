package by.zoomos.repository;

import by.zoomos.model.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с клиентами
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Находит клиента по коду
     */
    Optional<Client> findByCode(String code);

    /**
     * Находит всех активных клиентов
     */
    List<Client> findByActiveTrue();

    /**
     * Проверяет существование клиента с указанным кодом
     */
    boolean existsByCode(String code);
}