-- V1__Initial_Schema.sql

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

-- Таблица операций с файлами
CREATE TABLE file_operations (
                                 id BIGSERIAL PRIMARY KEY,
                                 client_id BIGINT REFERENCES clients(id),
                                 operation_type VARCHAR(50) NOT NULL,
                                 file_name VARCHAR(255) NOT NULL,
                                 file_type VARCHAR(50) NOT NULL,
                                 source_file_path TEXT,
                                 result_file_path TEXT,
                                 file_size BIGINT,
                                 status VARCHAR(50) NOT NULL,
                                 started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMP WITH TIME ZONE,
                                 processing_progress INT DEFAULT 0,
                                 total_records INT DEFAULT 0,
                                 processed_records INT DEFAULT 0,
                                 record_count INT,
                                 error_message TEXT,
                                 additional_info TEXT,
                                 entity_type VARCHAR(100)
);

-- Таблица этапов операций с файлами
CREATE TABLE file_operation_stages (
                                       operation_id BIGINT NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
                                       stage_order INT NOT NULL,
                                       name VARCHAR(50) NOT NULL,
                                       description VARCHAR(255),
                                       progress INT DEFAULT 0,
                                       started_at TIMESTAMP WITH TIME ZONE,
                                       completed_at TIMESTAMP WITH TIME ZONE,
                                       status VARCHAR(20) NOT NULL,
                                       PRIMARY KEY (operation_id, stage_order)
);

-- Таблица продуктов
CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          import_operation_id BIGINT,
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
                          product_additional5 VARCHAR(255)
);

-- Таблица региональных данных
CREATE TABLE region_data (
                             id BIGSERIAL PRIMARY KEY,
                             import_operation_id BIGINT,
                             client_id BIGINT,
                             region VARCHAR(255),
                             region_address VARCHAR(400),
                             product_id BIGINT REFERENCES products(id)
);

-- Таблица данных о конкурентах
CREATE TABLE competitor_data (
                                 id BIGSERIAL PRIMARY KEY,
                                 import_operation_id BIGINT,
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

-- Таблица шаблонов экспорта
CREATE TABLE export_templates (
                                  id BIGSERIAL PRIMARY KEY,
                                  name VARCHAR(255) NOT NULL,
                                  description TEXT,
                                  client_id BIGINT REFERENCES clients(id) ON DELETE CASCADE,
                                  entity_type VARCHAR(100) NOT NULL,
                                  strategy_id VARCHAR(255),
                                  is_default BOOLEAN DEFAULT FALSE,
                                  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  options_json TEXT NOT NULL DEFAULT '{}'
);

-- Таблица полей шаблонов экспорта
CREATE TABLE export_template_fields (
                                        template_id BIGINT NOT NULL,
                                        display_order INT NOT NULL,
                                        original_field VARCHAR(255),
                                        display_name VARCHAR(255),
                                        PRIMARY KEY (template_id, display_order)
);

-- Создание базовых индексов
CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_file_operations_client_id ON file_operations(client_id);
CREATE INDEX idx_file_operations_status ON file_operations(status);
CREATE INDEX idx_file_operations_started_at ON file_operations(started_at);

-- Индексы для продуктов
CREATE INDEX idx_product_client_id ON products(client_id);
CREATE INDEX idx_product_product_id ON products(product_id);
CREATE INDEX idx_product_name ON products(product_name);
CREATE INDEX idx_product_import_op ON products(import_operation_id);

-- Индексы для регионов
CREATE INDEX idx_region_client_id ON region_data(client_id);
CREATE INDEX idx_region_product_id ON region_data(product_id);
CREATE INDEX idx_region_region ON region_data(region);
CREATE INDEX idx_region_import_op ON region_data(import_operation_id);

-- Индексы для конкурентов
CREATE INDEX idx_competitor_client_id ON competitor_data(client_id);
CREATE INDEX idx_competitor_product_id ON competitor_data(product_id);
CREATE INDEX idx_competitor_name ON competitor_data(competitor_name);
CREATE INDEX idx_competitor_import_op ON competitor_data(import_operation_id);