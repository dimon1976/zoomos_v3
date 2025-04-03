-- src/main/resources/db/migration/V5__AddColumnFieldMappings.sql

-- Обновление таблицы file_operations для поддержки новой функциональности
ALTER TABLE field_mappings
    ADD COLUMN composite bool default false,
    ADD COLUMN related_entities VARCHAR(255);
