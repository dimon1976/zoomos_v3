package by.zoomos.service;

import by.zoomos.model.entity.Client;
import by.zoomos.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для работы с клиентами
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;

    /**
     * Создает нового клиента
     */
    @Transactional
    public Client createClient(Client client) {
        if (clientRepository.existsByCode(client.getCode())) {
            throw new IllegalArgumentException("Клиент с таким кодом уже существует: " + client.getCode());
        }
        log.info("Создание нового клиента: {}", client.getCode());
        return clientRepository.save(client);
    }

    /**
     * Обновляет информацию о клиенте
     */
    @Transactional
    public Client updateClient(Long id, Client client) {
        Client existingClient = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + id));

        // Проверяем код только если он изменился
        if (!existingClient.getCode().equals(client.getCode()) &&
                clientRepository.existsByCode(client.getCode())) {
            throw new IllegalArgumentException("Клиент с таким кодом уже существует: " + client.getCode());
        }

        existingClient.setName(client.getName());
        existingClient.setCode(client.getCode());
        existingClient.setDescription(client.getDescription());
        existingClient.setActive(client.isActive());

        log.info("Обновление клиента: {}", client.getCode());
        return clientRepository.save(existingClient);
    }

    /**
     * Деактивирует клиента
     */
    @Transactional
    public void deactivateClient(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + id));

        client.setActive(false);
        clientRepository.save(client);
        log.info("Деактивация клиента: {}", client.getCode());
    }

    /**
     * Получает информацию о клиенте по ID
     */
    @Transactional(readOnly = true)
    public Client getClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + id));
    }

    /**
     * Получает список всех активных клиентов
     */
    @Transactional(readOnly = true)
    public List<Client> getActiveClients() {
        return clientRepository.findByActiveTrue();
    }

    /**
     * Получает список всех клиентов
     */
    @Transactional(readOnly = true)
    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    /**
     * Проверяет существование клиента
     */
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return clientRepository.existsById(id);
    }
}