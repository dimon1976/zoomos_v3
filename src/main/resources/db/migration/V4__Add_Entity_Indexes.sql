-- src/main/resources/db/migration/V4__Add_Entity_Indexes.sql
-- Добавляем индексы для улучшения производительности запросов

-- Индексы для таблицы products
CREATE INDEX idx_product_client_id ON products(client_id);
CREATE INDEX idx_product_product_id ON products(product_id);
CREATE INDEX idx_product_name ON products(product_name);
CREATE INDEX idx_product_file_id ON products(file_id);
CREATE INDEX idx_product_brand ON products(product_brand);
CREATE INDEX idx_product_category1 ON products(product_category1);

-- Индексы для таблицы region_data
CREATE INDEX idx_region_client_id ON region_data(client_id);
CREATE INDEX idx_region_product_id ON region_data(product_id);
CREATE INDEX idx_region_region ON region_data(region);

-- Индексы для таблицы competitor_data
CREATE INDEX idx_competitor_client_id ON competitor_data(client_id);
CREATE INDEX idx_competitor_product_id ON competitor_data(product_id);
CREATE INDEX idx_competitor_name ON competitor_data(competitor_name);
CREATE INDEX idx_competitor_date ON competitor_data(competitor_local_date_time);

-- Обновление таблицы file_operations для связи с типом сущности
ALTER TABLE file_operations ADD COLUMN IF NOT EXISTS entity_type VARCHAR(100);