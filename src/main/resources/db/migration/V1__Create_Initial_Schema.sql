-- Создание базовых таблиц

-- Таблица клиентов
CREATE TABLE clients (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         description TEXT,
                         contact_email VARCHAR(255),
                         contact_phone VARCHAR(50),
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Таблица для хранения файловых операций
CREATE TABLE file_operations (
                                 id BIGSERIAL PRIMARY KEY,
                                 client_id INT REFERENCES clients(id),
                                 operation_type VARCHAR(50) NOT NULL, -- IMPORT, EXPORT, PROCESS
                                 file_name VARCHAR(255) NOT NULL,
                                 file_type VARCHAR(50) NOT NULL, -- CSV, EXCEL, etc.
                                 record_count INT,
                                 status VARCHAR(50) NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
                                 error_message TEXT,
                                 started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMP WITH TIME ZONE,
                                 created_by VARCHAR(255),

                                 CONSTRAINT file_operations_operation_type_check CHECK (operation_type IN ('IMPORT', 'EXPORT', 'PROCESS'))
);

-- Индексы
CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_file_operations_client_id ON file_operations(client_id);
CREATE INDEX idx_file_operations_status ON file_operations(status);
CREATE INDEX idx_file_operations_started_at ON file_operations(started_at);