-- V1__Initial_Schema.sql
-- Начальная миграция - создание базовой структуры БД

-- Создание таблицы клиентов
CREATE TABLE IF NOT EXISTS clients (
                                       id BIGSERIAL PRIMARY KEY,
                                       name VARCHAR(255) NOT NULL UNIQUE,
                                       description TEXT,
                                       contact_email VARCHAR(255),
                                       contact_phone VARCHAR(50),
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы файловых операций
CREATE TABLE IF NOT EXISTS file_operations (
                                               id BIGSERIAL PRIMARY KEY,
                                               client_id BIGINT NOT NULL,
                                               operation_type VARCHAR(20) NOT NULL,
                                               file_name VARCHAR(255) NOT NULL,
                                               file_type VARCHAR(50) NOT NULL,
                                               record_count INTEGER DEFAULT 0,
                                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                               error_message TEXT,
                                               started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                               completed_at TIMESTAMP WITH TIME ZONE,
                                               created_by VARCHAR(255),

    -- Дополнительные поля
                                               source_file_path TEXT,
                                               result_file_path TEXT,
                                               file_size BIGINT DEFAULT 0,
                                               total_records INTEGER DEFAULT 0,
                                               processed_records INTEGER DEFAULT 0,
                                               processing_progress INTEGER DEFAULT 0,
                                               field_mapping_id BIGINT,
                                               strategy_id BIGINT,
                                               processing_params TEXT,
                                               file_hash VARCHAR(64),

    -- Ограничения
                                               CONSTRAINT fk_file_operations_client
                                                   FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
                                               CONSTRAINT check_operation_type
                                                   CHECK (operation_type IN ('IMPORT', 'EXPORT', 'PROCESS')),
                                               CONSTRAINT check_status
                                                   CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
                                               CONSTRAINT check_progress
                                                   CHECK (processing_progress >= 0 AND processing_progress <= 100)
);

-- Создание таблицы продуктов
CREATE TABLE IF NOT EXISTS products (
                                        id BIGSERIAL PRIMARY KEY,
                                        client_id BIGINT NOT NULL,
                                        data_source VARCHAR(20) DEFAULT 'FILE',
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
                                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ограничения
                                        CONSTRAINT fk_products_client
                                            FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
                                        CONSTRAINT check_data_source
                                            CHECK (data_source IN ('TASK', 'REPORT', 'FILE', 'API', 'SYSTEM'))
);

-- Создание таблицы региональных данных
CREATE TABLE IF NOT EXISTS region_data (
                                           id BIGSERIAL PRIMARY KEY,
                                           client_id BIGINT NOT NULL,
                                           product_id BIGINT,
                                           region VARCHAR(255),
                                           region_address VARCHAR(400),

                                           created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                           updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ограничения
                                           CONSTRAINT fk_region_data_client
                                               FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
                                           CONSTRAINT fk_region_data_product
                                               FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Создание таблицы данных конкурентов
CREATE TABLE IF NOT EXISTS competitor_data (
                                               id BIGSERIAL PRIMARY KEY,
                                               client_id BIGINT NOT NULL,
                                               product_id BIGINT,

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
                                               updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ограничения
                                               CONSTRAINT fk_competitor_data_client
                                                   FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
                                               CONSTRAINT fk_competitor_data_product
                                                   FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Создание индексов для оптимизации производительности

-- Индексы для file_operations
CREATE INDEX IF NOT EXISTS idx_file_operations_client_id ON file_operations(client_id);
CREATE INDEX IF NOT EXISTS idx_file_operations_status ON file_operations(status);
CREATE INDEX IF NOT EXISTS idx_file_operations_started_at ON file_operations(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_file_operations_type ON file_operations(operation_type);
CREATE INDEX IF NOT EXISTS idx_file_operations_client_status ON file_operations(client_id, status);

-- Индексы для products
CREATE INDEX IF NOT EXISTS idx_products_client_id ON products(client_id);
CREATE INDEX IF NOT EXISTS idx_products_product_id ON products(product_id);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(product_name);
CREATE INDEX IF NOT EXISTS idx_products_brand ON products(product_brand);
CREATE INDEX IF NOT EXISTS idx_products_client_product_id ON products(client_id, product_id);

-- Индексы для region_data
CREATE INDEX IF NOT EXISTS idx_region_data_client_id ON region_data(client_id);
CREATE INDEX IF NOT EXISTS idx_region_data_product_id ON region_data(product_id);
CREATE INDEX IF NOT EXISTS idx_region_data_region ON region_data(region);

-- Индексы для competitor_data
CREATE INDEX IF NOT EXISTS idx_competitor_data_client_id ON competitor_data(client_id);
CREATE INDEX IF NOT EXISTS idx_competitor_data_product_id ON competitor_data(product_id);
CREATE INDEX IF NOT EXISTS idx_competitor_data_name ON competitor_data(competitor_name);

-- Вставка начальных данных
INSERT INTO clients (name, description, contact_email)
VALUES ('Демо клиент', 'Клиент для демонстрации функционала', 'demo@example.com')
ON CONFLICT (name) DO NOTHING;