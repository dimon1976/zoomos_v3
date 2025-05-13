-- V2__Additional_Tables.sql

-- Таблица для конфигураций сопоставления полей
CREATE TABLE field_mappings (
                                id BIGSERIAL PRIMARY KEY,
                                name VARCHAR(255) NOT NULL,
                                description TEXT,
                                client_id BIGINT REFERENCES clients(id),
                                entity_type VARCHAR(100) NOT NULL,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                is_active BOOLEAN DEFAULT TRUE,
                                composite BOOLEAN DEFAULT FALSE,
                                related_entities VARCHAR(255),
                                CONSTRAINT field_mappings_unique_name_client UNIQUE (name, client_id, entity_type)
);

-- Таблица для деталей сопоставления отдельных полей
CREATE TABLE field_mapping_details (
                                       id BIGSERIAL PRIMARY KEY,
                                       field_mapping_id BIGINT NOT NULL REFERENCES field_mappings(id) ON DELETE CASCADE,
                                       source_field VARCHAR(255) NOT NULL,
                                       target_field VARCHAR(255) NOT NULL,
                                       required BOOLEAN DEFAULT FALSE,
                                       transformation_type VARCHAR(100),
                                       transformation_params TEXT,
                                       validation_rules TEXT,
                                       default_value TEXT,
                                       order_index INT DEFAULT 0,
                                       CONSTRAINT field_mapping_details_unique_fields UNIQUE (field_mapping_id, source_field)
);

-- Таблица для стратегий обработки файлов
CREATE TABLE processing_strategies (
                                       id BIGSERIAL PRIMARY KEY,
                                       name VARCHAR(255) NOT NULL,
                                       description TEXT,
                                       strategy_type VARCHAR(100) NOT NULL,
                                       strategy_class VARCHAR(255) NOT NULL,
                                       parameters TEXT,
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       is_active BOOLEAN DEFAULT TRUE,
                                       CONSTRAINT processing_strategies_unique_name UNIQUE (name)
);

-- Таблица для статистики и метрик файловых операций
CREATE TABLE file_operation_stats (
                                      id BIGSERIAL PRIMARY KEY,
                                      file_operation_id BIGINT NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
                                      processed_records INT DEFAULT 0,
                                      successful_records INT DEFAULT 0,
                                      failed_records INT DEFAULT 0,
                                      processing_time_ms BIGINT DEFAULT 0,
                                      memory_used_kb INT DEFAULT 0,
                                      validation_errors INT DEFAULT 0,
                                      transformation_errors INT DEFAULT 0,
                                      database_errors INT DEFAULT 0,
                                      chunk_size INT DEFAULT 0,
                                      chunk_count INT DEFAULT 0,
                                      stats_details TEXT,
                                      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Таблица для хранения деталей ошибок обработки
CREATE TABLE file_processing_errors (
                                        id BIGSERIAL PRIMARY KEY,
                                        file_operation_id BIGINT NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
                                        error_type VARCHAR(100) NOT NULL,
                                        error_message TEXT NOT NULL,
                                        error_details TEXT,
                                        record_number INT,
                                        source_field VARCHAR(255),
                                        source_value TEXT,
                                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Обновление таблицы file_operations для поддержки новой функциональности
ALTER TABLE file_operations
    ADD COLUMN field_mapping_id BIGINT REFERENCES field_mappings(id),
    ADD COLUMN strategy_id BIGINT REFERENCES processing_strategies(id),
    ADD COLUMN file_hash VARCHAR(64),
    ADD COLUMN processing_params TEXT;

-- Создание индексов для новых таблиц
CREATE INDEX idx_field_mappings_client_id ON field_mappings(client_id);
CREATE INDEX idx_field_mapping_details_mapping_id ON field_mapping_details(field_mapping_id);
CREATE INDEX idx_file_operation_stats_operation_id ON file_operation_stats(file_operation_id);
CREATE INDEX idx_file_processing_errors_operation_id ON file_processing_errors(file_operation_id);
CREATE INDEX idx_file_operations_field_mapping_id ON file_operations(field_mapping_id);
CREATE INDEX idx_file_operations_strategy_id ON file_operations(strategy_id);