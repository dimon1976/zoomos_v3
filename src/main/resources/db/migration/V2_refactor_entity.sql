-- Скрипт миграции данных из старых таблиц (region_data и competitor_data) в новую (market_data)

-- Создание новой таблицы market_data
CREATE TABLE market_data (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             client_id BIGINT,
                             product_id BIGINT,
                             region VARCHAR(255),
                             region_address VARCHAR(400),
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
                             FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Создание индексов для оптимизации запросов
CREATE INDEX idx_market_region ON market_data(region);
CREATE INDEX idx_market_competitor ON market_data(competitor_name);
CREATE INDEX idx_market_product ON market_data(product_id);

-- Миграция данных из region_data
INSERT INTO market_data (client_id, product_id, region, region_address)
SELECT rd.client_id, rd.product_id, rd.region, rd.region_address
FROM region_data rd;

-- Миграция данных из competitor_data
INSERT INTO market_data (
    client_id,
    product_id,
    competitor_name,
    competitor_price,
    competitor_promotional_price,
    competitor_time,
    competitor_date,
    competitor_local_date_time,
    competitor_stock_status,
    competitor_additional_price,
    competitor_commentary,
    competitor_product_name,
    competitor_additional,
    competitor_additional2,
    competitor_url,
    competitor_web_cache_url
)
SELECT
    cd.client_id,
    cd.product_id,
    cd.competitor_name,
    cd.competitor_price,
    cd.competitor_promotional_price,
    cd.competitor_time,
    cd.competitor_date,
    cd.competitor_local_date_time,
    cd.competitor_stock_status,
    cd.competitor_additional_price,
    cd.competitor_commentary,
    cd.competitor_product_name,
    cd.competitor_additional,
    cd.competitor_additional2,
    cd.competitor_url,
    cd.competitor_web_cache_url
FROM competitor_data cd;

-- После успешной миграции и проверки данных, можно удалить старые таблицы:
-- DROP TABLE region_data;
-- DROP TABLE competitor_data;