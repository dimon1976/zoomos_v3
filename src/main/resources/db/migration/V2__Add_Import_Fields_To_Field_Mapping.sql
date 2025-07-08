-- src/main/resources/db/migration/V2__Add_Import_Fields_To_Field_Mapping.sql

-- Добавляем недостающие поля в таблицу field_mappings
ALTER TABLE field_mappings
    ADD COLUMN IF NOT EXISTS import_type VARCHAR(100) DEFAULT 'COMBINED',
    ADD COLUMN IF NOT EXISTS file_encoding VARCHAR(50) DEFAULT 'UTF-8',
    ADD COLUMN IF NOT EXISTS csv_delimiter VARCHAR(5) DEFAULT ';',
    ADD COLUMN IF NOT EXISTS csv_quote_char VARCHAR(5) DEFAULT '"',
    ADD COLUMN IF NOT EXISTS duplicate_strategy VARCHAR(50) DEFAULT 'SKIP',
    ADD COLUMN IF NOT EXISTS additional_params TEXT;

-- Добавляем поле target_entity в таблицу field_mapping_details
ALTER TABLE field_mapping_details
    ADD COLUMN IF NOT EXISTS target_entity VARCHAR(50);

-- Обновляем ограничение уникальности с учетом нового поля import_type
ALTER TABLE field_mappings
    DROP CONSTRAINT IF EXISTS field_mappings_unique_name_client;

ALTER TABLE field_mappings
    ADD CONSTRAINT field_mappings_unique_name_client
        UNIQUE (name, client_id);

-- Добавляем индексы для новых полей
CREATE INDEX IF NOT EXISTS idx_field_mappings_import_type ON field_mappings(import_type);
CREATE INDEX IF NOT EXISTS idx_field_mappings_duplicate_strategy ON field_mappings(duplicate_strategy);
CREATE INDEX IF NOT EXISTS idx_field_mapping_details_target_entity ON field_mapping_details(target_entity);

-- Комментарии к полям
COMMENT ON COLUMN field_mappings.import_type IS 'Тип импорта: COMBINED (составной) или SINGLE (отдельная сущность)';
COMMENT ON COLUMN field_mappings.file_encoding IS 'Кодировка файла (UTF-8, Windows-1251, и т.д.)';
COMMENT ON COLUMN field_mappings.csv_delimiter IS 'Разделитель полей в CSV файле';
COMMENT ON COLUMN field_mappings.csv_quote_char IS 'Символ кавычек в CSV файле';
COMMENT ON COLUMN field_mappings.duplicate_strategy IS 'Стратегия обработки дубликатов: SKIP, OVERRIDE, IGNORE';
COMMENT ON COLUMN field_mapping_details.target_entity IS 'Целевая сущность для составных шаблонов: PRODUCT, COMPETITOR, REGION';