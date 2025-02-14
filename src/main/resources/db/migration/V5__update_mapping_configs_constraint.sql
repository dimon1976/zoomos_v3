-- V5__update_mapping_configs_constraint.sql
-- Удаляем старое ограничение
ALTER TABLE mapping_configs DROP CONSTRAINT unique_client_default_mapping;

-- Добавляем новое ограничение, которое учитывает только активные записи
CREATE UNIQUE INDEX unique_client_default_mapping
    ON mapping_configs (client_id)
    WHERE is_default = true AND active = true;
