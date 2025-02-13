-- V2__add_processing_status.sql

CREATE TABLE processing_status (
                                   id BIGSERIAL PRIMARY KEY,
                                   client_id BIGINT NOT NULL,
                                   file_name VARCHAR(255) NOT NULL,
                                   file_size BIGINT,
                                   status VARCHAR(20) NOT NULL,
                                   processed_records INTEGER,
                                   total_records INTEGER,
                                   error_message TEXT,
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP,
                                   completed_at TIMESTAMP
);

-- Индексы
CREATE INDEX idx_processing_status_client_id ON processing_status(client_id);
CREATE INDEX idx_processing_status_status ON processing_status(status);
CREATE INDEX idx_processing_status_created_at ON processing_status(created_at);