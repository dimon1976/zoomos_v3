-- src/main/resources/db/migration/V3__Add_Timestamps_To_Entity_Tables.sql

-- Добавляем временные метки в таблицу products
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Добавляем временные метки в таблицу competitor_data
ALTER TABLE competitor_data
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Добавляем временные метки в таблицу region_data
ALTER TABLE region_data
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Создаем функцию для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Создаем триггеры для автоматического обновления updated_at при UPDATE
CREATE TRIGGER update_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_competitor_data_updated_at
    BEFORE UPDATE ON competitor_data
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_region_data_updated_at
    BEFORE UPDATE ON region_data
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Заполняем существующие записи текущим временем
UPDATE products SET
                    created_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

UPDATE competitor_data SET
                           created_at = CURRENT_TIMESTAMP,
                           updated_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

UPDATE region_data SET
                       created_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

-- Комментарии к полям
COMMENT ON COLUMN products.created_at IS 'Время создания записи';
COMMENT ON COLUMN products.updated_at IS 'Время последнего обновления записи';
COMMENT ON COLUMN competitor_data.created_at IS 'Время создания записи';
COMMENT ON COLUMN competitor_data.updated_at IS 'Время последнего обновления записи';
COMMENT ON COLUMN region_data.created_at IS 'Время создания записи';
COMMENT ON COLUMN region_data.updated_at IS 'Время последнего обновления записи';