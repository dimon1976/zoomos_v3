-- V1__initial_schema.sql

-- Создание таблицы products
CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          client_id BIGINT NOT NULL,
                          product_id VARCHAR(255) NOT NULL UNIQUE,
                          model VARCHAR(255) NOT NULL,
                          brand VARCHAR(255) NOT NULL,
                          base_price DECIMAL(19,2),
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP
);

-- Создание таблицы region_data
CREATE TABLE region_data (
                             id BIGSERIAL PRIMARY KEY,
                             product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                             region VARCHAR(255) NOT NULL,
                             regional_price DECIMAL(19,2),
                             stock_amount INTEGER,
                             warehouse VARCHAR(255),
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы competitor_data
CREATE TABLE competitor_data (
                                 id BIGSERIAL PRIMARY KEY,
                                 product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                                 competitor_name VARCHAR(255) NOT NULL,
                                 competitor_url VARCHAR(1024),
                                 competitor_price DECIMAL(19,2),
                                 competitor_promo_price DECIMAL(19,2),
                                 parsed_at TIMESTAMP,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Создание индексов
CREATE INDEX idx_products_client_id ON products(client_id);
CREATE INDEX idx_region_data_product_region ON region_data(product_id, region);
CREATE INDEX idx_competitor_data_product_competitor ON competitor_data(product_id, competitor_name);