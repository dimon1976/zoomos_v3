-- src/main/resources/db/migration/V7__Add_Import_Operation_Id.sql
ALTER TABLE products
    ADD COLUMN import_operation_id BIGINT;

ALTER TABLE region_data
    ADD COLUMN import_operation_id BIGINT;

ALTER TABLE competitor_data
    ADD COLUMN import_operation_id BIGINT;

-- Создаем индексы для улучшения производительности запросов с фильтрацией по операциям
CREATE INDEX idx_product_import_op ON products(import_operation_id);
CREATE INDEX idx_region_import_op ON region_data(import_operation_id);
CREATE INDEX idx_competitor_import_op ON competitor_data(import_operation_id);

-- Добавляем внешний ключ для связи с операциями (опционально)
ALTER TABLE products
    ADD CONSTRAINT fk_product_import_op
        FOREIGN KEY (import_operation_id)
            REFERENCES file_operations(id);

ALTER TABLE region_data
    ADD CONSTRAINT fk_region_import_op
        FOREIGN KEY (import_operation_id)
            REFERENCES file_operations(id);

ALTER TABLE competitor_data
    ADD CONSTRAINT fk_competitor_import_op
        FOREIGN KEY (import_operation_id)
            REFERENCES file_operations(id);