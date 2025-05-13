-- V3__Add_Foreign_Keys.sql

-- Добавление внешних ключей для связи с клиентами
ALTER TABLE products
    ADD CONSTRAINT fk_product_client
        FOREIGN KEY (client_id)
            REFERENCES clients(id);

ALTER TABLE region_data
    ADD CONSTRAINT fk_region_client
        FOREIGN KEY (client_id)
            REFERENCES clients(id);

ALTER TABLE competitor_data
    ADD CONSTRAINT fk_competitor_client
        FOREIGN KEY (client_id)
            REFERENCES clients(id);

-- Добавление внешних ключей для связи с операциями импорта
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

-- Связь между шаблонами экспорта и полями
ALTER TABLE export_template_fields
    ADD CONSTRAINT fk_export_template_fields
        FOREIGN KEY (template_id)
            REFERENCES export_templates(id)
            ON DELETE CASCADE;