package my.java.service.client.impl;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.dto.ClientDto;
import my.java.model.Client;
import my.java.repository.ClientRepository;
import my.java.service.client.ClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ClientDto> getAllClients() {
        log.debug("Getting all clients");
        List<Object[]> clientsWithCounts = clientRepository.findClientsWithFileOperationCount();

        return clientsWithCounts.stream()
                .map(this::buildClientDtoFromQueryResult)
                .collect(Collectors.toList());
    }

    private ClientDto buildClientDtoFromQueryResult(Object[] result) {
        Client client = (Client) result[0];
        Long count = (Long) result[1];
        return mapToDto(client, count.intValue());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDto> getClientById(Long id) {
        log.debug("Getting client by id: {}", id);
        return clientRepository.findById(id)
                .map(client -> mapToDto(client, client.getFileOperations().size()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDto> getClientByName(String name) {
        log.debug("Getting client by name: {}", name);
        return clientRepository.findByNameIgnoreCase(name)
                .map(client -> mapToDto(client, client.getFileOperations().size()));
    }

    @Override
    @Transactional
    public ClientDto createClient(ClientDto clientDto) {
        log.debug("Creating new client: {}", clientDto.getName());
        validateClientNameUniqueness(clientDto.getName());

        Client client = mapToEntity(clientDto);
        Client savedClient = clientRepository.save(client);

        log.info("Created new client with id: {}", savedClient.getId());
        return mapToDto(savedClient, 0);
    }

    private void validateClientNameUniqueness(String clientName) {
        if (clientRepository.existsByNameIgnoreCase(clientName)) {
            throw new IllegalArgumentException("Клиент с именем '" + clientName + "' уже существует");
        }
    }

    @Override
    @Transactional
    public ClientDto updateClient(Long id, ClientDto clientDto) {
        log.debug("Updating client with id: {}", id);

        Client client = findClientEntityById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент с ID " + id + " не найден"));

        validateClientNameForUpdate(client, clientDto);
        updateClientFields(client, clientDto);

        Client updatedClient = clientRepository.save(client);
        log.info("Updated client with id: {}", id);

        return mapToDto(updatedClient, updatedClient.getFileOperations().size());
    }

    private void validateClientNameForUpdate(Client existingClient, ClientDto clientDto) {
        if (!existingClient.getName().equalsIgnoreCase(clientDto.getName()) &&
                clientRepository.existsByNameIgnoreCase(clientDto.getName())) {
            throw new IllegalArgumentException("Клиент с именем '" + clientDto.getName() + "' уже существует");
        }
    }

    private void updateClientFields(Client client, ClientDto clientDto) {
        client.setName(clientDto.getName());
        client.setDescription(clientDto.getDescription());
        client.setContactEmail(clientDto.getContactEmail());
        client.setContactPhone(clientDto.getContactPhone());
    }

    @Override
    @Transactional
    public boolean deleteClient(Long id) {
        log.debug("Deleting client with id: {}", id);

        if (!clientRepository.existsById(id)) {
            log.warn("Client with id: {} not found for deletion", id);
            return false;
        }

        clientRepository.deleteById(id);
        log.info("Deleted client with id: {}", id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientDto> searchClients(String namePart) {
        log.debug("Searching clients by name part: {}", namePart);

        List<Client> clients = clientRepository.findByNameContainingIgnoreCase(namePart);
        return clients.stream()
                .map(client -> mapToDto(client, client.getFileOperations().size()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Client> findClientEntityById(Long id) {
        log.debug("Finding client entity by id: {}", id);
        return clientRepository.findById(id);
    }

    /**
     * Маппер из entity в DTO
     */
    private ClientDto mapToDto(Client client, Integer fileOperationsCount) {
        return ClientDto.builder()
                .id(client.getId())
                .name(client.getName())
                .description(client.getDescription())
                .contactEmail(client.getContactEmail())
                .contactPhone(client.getContactPhone())
                .fileOperationsCount(fileOperationsCount)
                .build();
    }

    /**
     * Маппер из DTO в entity
     */
    private Client mapToEntity(ClientDto dto) {
        return Client.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .contactEmail(dto.getContactEmail())
                .contactPhone(dto.getContactPhone())
                .build();
    }
}