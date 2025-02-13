-- V3__create_clients_table.sql
CREATE TABLE clients (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         code VARCHAR(50) NOT NULL UNIQUE,
                         description TEXT,
                         active BOOLEAN DEFAULT true,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP
);

-- Добавляем внешний ключ в products
ALTER TABLE products
    ADD CONSTRAINT fk_products_client
        FOREIGN KEY (client_id)
            REFERENCES clients(id);

-- Создаем индекс для оптимизации поиска
CREATE INDEX idx_clients_code ON clients(code);
CREATE INDEX idx_clients_active ON clients(active);