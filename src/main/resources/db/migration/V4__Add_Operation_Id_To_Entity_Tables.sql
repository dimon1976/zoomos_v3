-- src/main/resources/db/migration/V4__Add_Operation_Id_To_Entity_Tables.sql

-- Добавляем универсальное поле operation_id в таблицы сущностей
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS operation_id BIGINT REFERENCES file_operations(id);

-- Индексы для быстрого поиска по операции
CREATE INDEX IF NOT EXISTS idx_products_operation ON products(operation_id);

-- Составные индексы для фильтрации по клиенту + операции
CREATE INDEX IF NOT EXISTS idx_products_client_operation ON products(client_id, operation_id);

-- Комментарии
COMMENT ON COLUMN products.operation_id IS 'ID файловой операции (ссылка на file_operations)';
