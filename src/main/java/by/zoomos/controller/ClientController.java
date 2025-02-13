package by.zoomos.controller;

import by.zoomos.model.entity.Client;
import by.zoomos.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST контроллер для работы с клиентами
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    public ResponseEntity<Client> createClient(@RequestBody Client client) {
        log.info("Создание нового клиента");
        return ResponseEntity.ok(clientService.createClient(client));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Client> updateClient(
            @PathVariable Long id,
            @RequestBody Client client) {
        log.info("Обновление клиента: {}", id);
        return ResponseEntity.ok(clientService.updateClient(id, client));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateClient(@PathVariable Long id) {
        log.info("Деактивация клиента: {}", id);
        clientService.deactivateClient(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Client> getClient(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClient(id));
    }

    @GetMapping("/active")
    public ResponseEntity<List<Client>> getActiveClients() {
        return ResponseEntity.ok(clientService.getActiveClients());
    }

    @GetMapping
    public ResponseEntity<List<Client>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }
}