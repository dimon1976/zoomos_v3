-- src/main/resources/db/migration/V1__Create_Complete_Schema.sql

-- Создание таблицы клиентов
CREATE TABLE clients (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL UNIQUE,
                         description TEXT,
                         contact_email VARCHAR(255),
                         contact_phone VARCHAR(50),
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы файловых операций
CREATE TABLE file_operations (
                                 id BIGSERIAL PRIMARY KEY,
                                 client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
                                 operation_type VARCHAR(20) NOT NULL CHECK (operation_type IN ('IMPORT', 'EXPORT', 'PROCESS')),
                                 file_name VARCHAR(255) NOT NULL,
                                 file_type VARCHAR(50) NOT NULL,
                                 record_count INTEGER DEFAULT 0,
                                 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
                                 error_message TEXT,
                                 started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMP WITH TIME ZONE,
                                 created_by VARCHAR(255),

    -- Дополнительные поля для расширенной функциональности
                                 source_file_path TEXT,
                                 result_file_path TEXT,
                                 file_size BIGINT DEFAULT 0,
                                 total_records INTEGER DEFAULT 0,
                                 processed_records INTEGER DEFAULT 0,
                                 processing_progress INTEGER DEFAULT 0 CHECK (processing_progress >= 0 AND processing_progress <= 100),
                                 field_mapping_id BIGINT,
                                 strategy_id BIGINT,
                                 processing_params TEXT,
                                 file_hash VARCHAR(64)
);

-- Создание таблицы продуктов
CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
                          data_source VARCHAR(20) DEFAULT 'FILE' CHECK (data_source IN ('TASK', 'REPORT', 'FILE', 'API', 'SYSTEM')),
                          file_id BIGINT,

    -- Основные поля продукта
                          product_id VARCHAR(255),
                          product_name VARCHAR(400),
                          product_brand VARCHAR(255),
                          product_bar VARCHAR(255),
                          product_description TEXT,
                          product_url VARCHAR(1100),
                          product_category1 VARCHAR(255),
                          product_category2 VARCHAR(255),
                          product_category3 VARCHAR(255),
                          product_price DECIMAL(15,2),
                          product_analog VARCHAR(255),

    -- Дополнительные поля
                          product_additional1 VARCHAR(255),
                          product_additional2 VARCHAR(255),
                          product_additional3 VARCHAR(255),
                          product_additional4 VARCHAR(255),
                          product_additional5 VARCHAR(255),

                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы региональных данных
CREATE TABLE region_data (
                             id BIGSERIAL PRIMARY KEY,
                             client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
                             product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
                             region VARCHAR(255),
                             region_address VARCHAR(400),

                             created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы данных конкурентов
CREATE TABLE competitor_data (
                                 id BIGSERIAL PRIMARY KEY,
                                 client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
                                 product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,

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

                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы для конфигураций сопоставления полей
CREATE TABLE field_mappings (
                                id BIGSERIAL PRIMARY KEY,
                                name VARCHAR(255) NOT NULL,
                                description TEXT,
                                client_id BIGINT REFERENCES clients(id) ON DELETE CASCADE,
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
                                       order_index INTEGER DEFAULT 0,

                                       CONSTRAINT field_mapping_details_unique_fields UNIQUE (field_mapping_id, source_field)
);

-- Создание таблицы для стратегий обработки файлов
CREATE TABLE processing_strategies (
                                       id BIGSERIAL PRIMARY KEY,
                                       name VARCHAR(255) NOT NULL UNIQUE,
                                       description TEXT,
                                       strategy_type VARCHAR(100) NOT NULL,
                                       strategy_class VARCHAR(255) NOT NULL,
                                       parameters TEXT,
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       is_active BOOLEAN DEFAULT TRUE
);

-- Создание таблицы для статистики файловых операций
CREATE TABLE file_operation_stats (
                                      id BIGSERIAL PRIMARY KEY,
                                      file_operation_id BIGINT NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
                                      processed_records INTEGER DEFAULT 0,
                                      successful_records INTEGER DEFAULT 0,
                                      failed_records INTEGER DEFAULT 0,
                                      processing_time_ms BIGINT DEFAULT 0,
                                      memory_used_kb INTEGER DEFAULT 0,

                                      validation_errors INTEGER DEFAULT 0,
                                      transformation_errors INTEGER DEFAULT 0,
                                      database_errors INTEGER DEFAULT 0,

                                      chunk_size INTEGER DEFAULT 0,
                                      chunk_count INTEGER DEFAULT 0,

                                      stats_details TEXT,
                                      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы для ошибок обработки
CREATE TABLE file_processing_errors (
                                        id BIGSERIAL PRIMARY KEY,
                                        file_operation_id BIGINT NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
                                        error_type VARCHAR(100) NOT NULL,
                                        error_message TEXT NOT NULL,
                                        error_details TEXT,
                                        record_number INTEGER,
                                        source_field VARCHAR(255),
                                        source_value TEXT,
                                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Создание индексов для оптимизации производительности

-- Индексы для clients
CREATE INDEX idx_clients_name ON clients(name);

-- Индексы для file_operations
CREATE INDEX idx_file_operations_client_id ON file_operations(client_id);
CREATE INDEX idx_file_operations_status ON file_operations(status);
CREATE INDEX idx_file_operations_started_at ON file_operations(started_at DESC);
CREATE INDEX idx_file_operations_type ON file_operations(operation_type);
CREATE INDEX idx_file_operations_client_status ON file_operations(client_id, status);

-- Индексы для products
CREATE INDEX idx_products_client_id ON products(client_id);
CREATE INDEX idx_products_product_id ON products(product_id);
CREATE INDEX idx_products_name ON products(product_name);
CREATE INDEX idx_products_file_id ON products(file_id);
CREATE INDEX idx_products_brand ON products(product_brand);
CREATE INDEX idx_products_category1 ON products(product_category1);
CREATE INDEX idx_products_client_product_id ON products(client_id, product_id);

-- Индексы для region_data
CREATE INDEX idx_region_data_client_id ON region_data(client_id);
CREATE INDEX idx_region_data_product_id ON region_data(product_id);
CREATE INDEX idx_region_data_region ON region_data(region);
CREATE INDEX idx_region_data_client_region ON region_data(client_id, region);

-- Индексы для competitor_data
CREATE INDEX idx_competitor_data_client_id ON competitor_data(client_id);
CREATE INDEX idx_competitor_data_product_id ON competitor_data(product_id);
CREATE INDEX idx_competitor_data_name ON competitor_data(competitor_name);
CREATE INDEX idx_competitor_data_date ON competitor_data(competitor_local_date_time DESC);
CREATE INDEX idx_competitor_data_client_name ON competitor_data(client_id, competitor_name);

-- Индексы для field_mappings
CREATE INDEX idx_field_mappings_client_id ON field_mappings(client_id);
CREATE INDEX idx_field_mappings_entity_type ON field_mappings(entity_type);
CREATE INDEX idx_field_mappings_active ON field_mappings(is_active);

-- Индексы для field_mapping_details
CREATE INDEX idx_field_mapping_details_mapping_id ON field_mapping_details(field_mapping_id);
CREATE INDEX idx_field_mapping_details_order ON field_mapping_details(field_mapping_id, order_index);

-- Индексы для processing_strategies
CREATE INDEX idx_processing_strategies_type ON processing_strategies(strategy_type);
CREATE INDEX idx_processing_strategies_active ON processing_strategies(is_active);

-- Индексы для file_operation_stats
CREATE INDEX idx_file_operation_stats_operation_id ON file_operation_stats(file_operation_id);

-- Индексы для file_processing_errors
CREATE INDEX idx_file_processing_errors_operation_id ON file_processing_errors(file_operation_id);
CREATE INDEX idx_file_processing_errors_type ON file_processing_errors(error_type);
CREATE INDEX idx_file_processing_errors_record ON file_processing_errors(record_number);

-- Вставка тестовых данных
INSERT INTO clients (name, description, contact_email) VALUES
    ('Тестовый клиент', 'Клиент для тестирования системы импорта', 'test@example.com');

-- Вставка базовых стратегий обработки
INSERT INTO processing_strategies (name, description, strategy_type, strategy_class) VALUES
                                                                                         ('CSV Import', 'Стандартный импорт CSV файлов', 'IMPORT', 'CsvImportProcessor'),
                                                                                         ('Combined Import', 'Импорт связанных сущностей из одного файла', 'IMPORT', 'CombinedEntityProcessor'),
                                                                                         ('Excel Import', 'Импорт Excel файлов', 'IMPORT', 'ExcelImportProcessor'),
                                                                                         ('CSV Export', 'Экспорт данных в CSV', 'EXPORT', 'CsvExportProcessor');