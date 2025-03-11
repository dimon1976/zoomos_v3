-- Базовая схема системы

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
                                 client_id BIGINT REFERENCES clients(id),
                                 operation_type VARCHAR(50) NOT NULL,
                                 file_name VARCHAR(255) NOT NULL,
                                 file_type VARCHAR(50) NOT NULL,
                                 record_count INT,
                                 status VARCHAR(50) NOT NULL,
                                 error_message TEXT,
                                 started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMP WITH TIME ZONE,
                                 created_by VARCHAR(255),
                                 field_mapping_id BIGINT,
                                 strategy_id BIGINT,
                                 total_records INT DEFAULT 0,
                                 processed_records INT DEFAULT 0,
                                 processing_progress INT DEFAULT 0,
                                 file_hash VARCHAR(64),
                                 file_size BIGINT DEFAULT 0,
                                 processing_params TEXT,
                                 source_file_path TEXT,
                                 result_file_path TEXT,
                                 entity_type VARCHAR(100),
                                 CONSTRAINT file_operations_operation_type_check CHECK (operation_type IN ('IMPORT', 'EXPORT', 'PROCESS'))
);

-- Таблица для сущностей продуктов
CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          data_source VARCHAR(50),
                          file_id BIGINT,
                          client_id BIGINT,
                          product_id VARCHAR(255),
                          product_name VARCHAR(400),
                          product_brand VARCHAR(255),
                          product_bar VARCHAR(255),
                          product_description TEXT,
                          product_url VARCHAR(1100),
                          product_category1 VARCHAR(255),
                          product_category2 VARCHAR(255),
                          product_category3 VARCHAR(255),
                          product_price DOUBLE PRECISION,
                          product_analog VARCHAR(255),
                          product_additional1 VARCHAR(255),
                          product_additional2 VARCHAR(255),
                          product_additional3 VARCHAR(255),
                          product_additional4 VARCHAR(255),
                          product_additional5 VARCHAR(255),
                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Таблица для данных о регионах
CREATE TABLE region_data (
                             id BIGSERIAL PRIMARY KEY,
                             client_id BIGINT,
                             region VARCHAR(255),
                             region_address VARCHAR(400),
                             product_id BIGINT REFERENCES products(id)
);

-- Таблица для данных о конкурентах
CREATE TABLE competitor_data (
                                 id BIGSERIAL PRIMARY KEY,
                                 client_id BIGINT,
                                 competitor_name VARCHAR(400),
                                 competitor_price VARCHAR(255),
                                 competitor_promotional_price VARCHAR(255),
                                 competitor_time VARCHAR(255),
                                 competitor_date VARCHAR(255),
                                 competitor_local_date_time TIMESTAMP,
                                 competitor_stock_status VARCHAR(255),
                                 competitor_additional_price VARCHAR(255),
                                 competitor_commentary VARCHAR(1000),
                                 competitor_product_name VARCHAR(400),
                                 competitor_additional VARCHAR(255),
                                 competitor_additional2 VARCHAR(255),
                                 competitor_url VARCHAR(1200),
                                 competitor_web_cache_url VARCHAR(1200),
                                 product_id BIGINT REFERENCES products(id)
);

-- Создание таблицы для конфигураций сопоставления полей
CREATE TABLE field_mappings (
                                id BIGSERIAL PRIMARY KEY,
                                name VARCHAR(255) NOT NULL,
                                description TEXT,
                                client_id BIGINT REFERENCES clients(id),
                                entity_type VARCHAR(100) NOT NULL,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                is_active BOOLEAN DEFAULT TRUE,
                                CONSTRAINT field_mappings_unique_name_client UNIQUE (name, client_id, entity_type)
);

-- Создание таблицы для деталей сопоставления отдельных полей
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

-- Создание таблицы для стратегий обработки файлов
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

-- Создание таблицы для статистики и метрик файловых операций
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

-- Создание таблицы для хранения деталей ошибок обработки
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

-- Все индексы из ваших миграций
CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_file_operations_client_id ON file_operations(client_id);
CREATE INDEX idx_file_operations_status ON file_operations(status);
CREATE INDEX idx_file_operations_started_at ON file_operations(started_at);
CREATE INDEX idx_field_mappings_client_id ON field_mappings(client_id);
CREATE INDEX idx_field_mapping_details_mapping_id ON field_mapping_details(field_mapping_id);
CREATE INDEX idx_file_operation_stats_operation_id ON file_operation_stats(file_operation_id);
CREATE INDEX idx_file_processing_errors_operation_id ON file_processing_errors(file_operation_id);
CREATE INDEX idx_file_operations_field_mapping_id ON file_operations(field_mapping_id);
CREATE INDEX idx_file_operations_strategy_id ON file_operations(strategy_id);
CREATE INDEX idx_product_client_id ON products(client_id);
CREATE INDEX idx_product_product_id ON products(product_id);
CREATE INDEX idx_product_name ON products(product_name);
CREATE INDEX idx_product_file_id ON products(file_id);
CREATE INDEX idx_product_brand ON products(product_brand);
CREATE INDEX idx_product_category1 ON products(product_category1);
CREATE INDEX idx_region_client_id ON region_data(client_id);
CREATE INDEX idx_region_product_id ON region_data(product_id);
CREATE INDEX idx_region_region ON region_data(region);
CREATE INDEX idx_competitor_client_id ON competitor_data(client_id);
CREATE INDEX idx_competitor_product_id ON competitor_data(product_id);
CREATE INDEX idx_competitor_name ON competitor_data(competitor_name);
CREATE INDEX idx_competitor_date ON competitor_data(competitor_local_date_time);