-- V4__Fix_Constraints.sql

-- Сначала добавляем недостающий столбец is_active
ALTER TABLE export_templates
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

-- Создание условного ограничения уникальности для шаблонов экспорта
CREATE UNIQUE INDEX idx_unique_active_template
    ON export_templates (name, client_id, entity_type)
    WHERE is_active = true;

-- Добавление уникального индекса для продуктов по ID у одного клиента
CREATE UNIQUE INDEX idx_unique_product_id_per_client
    ON products (client_id, product_id)
    WHERE product_id IS NOT NULL;