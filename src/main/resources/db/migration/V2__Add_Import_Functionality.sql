-- V2__Add_Import_Functionality.sql
-- Добавление функционала импорта файлов и системы шаблонов маппинга

-- Создание таблицы шаблонов маппинга полей
CREATE TABLE IF NOT EXISTS field_mapping_templates (
                                                       id BIGSERIAL PRIMARY KEY,
                                                       name VARCHAR(255) NOT NULL,
                                                       description TEXT,
                                                       client_id BIGINT,
                                                       entity_type VARCHAR(50) NOT NULL,
                                                       file_format VARCHAR(20),
                                                       is_active BOOLEAN DEFAULT true,
                                                       is_default BOOLEAN DEFAULT false,
                                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ограничения
                                                       CONSTRAINT fk_field_mapping_templates_client
                                                           FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
                                                       CONSTRAINT unique_template_name_per_client
                                                           UNIQUE(client_id, name)
);

-- Создание таблицы правил маппинга
CREATE TABLE IF NOT EXISTS field_mapping_rules (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   template_id BIGINT NOT NULL,
                                                   csv_header VARCHAR(255) NOT NULL,
                                                   entity_field VARCHAR(255) NOT NULL,
                                                   entity_type VARCHAR(50),
                                                   field_type VARCHAR(50),
                                                   transformation_params TEXT,
                                                   is_required BOOLEAN DEFAULT false,
                                                   default_value VARCHAR(255),
                                                   validation_rules TEXT,
                                                   order_index INTEGER DEFAULT 0,
                                                   is_active BOOLEAN DEFAULT true,

    -- Ограничения
                                                   CONSTRAINT fk_field_mapping_rules_template
                                                       FOREIGN KEY (template_id) REFERENCES field_mapping_templates(id) ON DELETE CASCADE,
                                                   CONSTRAINT unique_csv_header_per_template
                                                       UNIQUE(template_id, csv_header)
);

-- Создание таблицы для статистики файловых операций
CREATE TABLE IF NOT EXISTS file_operation_stats (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    file_operation_id BIGINT NOT NULL,
                                                    processed_records INTEGER DEFAULT 0,
                                                    successful_records INTEGER DEFAULT 0,
                                                    failed_records INTEGER DEFAULT 0,
                                                    processing_time_ms BIGINT DEFAULT 0,
                                                    memory_used_kb INTEGER DEFAULT 0,

                                                    validation_errors INTEGER DEFAULT 0,
                                                    transformation_errors INTEGER DEFAULT 0,
                                                    database_errors INTEGER DEFAULT 0,

                                                    chunk_size INTEGER DEFAULT 0,
                                                    chunk_count INTEGER DEFAULT 0,

                                                    stats_details TEXT,
                                                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ограничения
                                                    CONSTRAINT fk_file_operation_stats_operation
                                                        FOREIGN KEY (file_operation_id) REFERENCES file_operations(id) ON DELETE CASCADE
);

-- Создание таблицы для ошибок обработки
CREATE TABLE IF NOT EXISTS file_processing_errors (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      file_operation_id BIGINT NOT NULL,
                                                      error_type VARCHAR(100) NOT NULL,
                                                      error_message TEXT NOT NULL,
                                                      error_details TEXT,
                                                      record_number INTEGER,
                                                      source_field VARCHAR(255),
                                                      source_value TEXT,
                                                      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ограничения
                                                      CONSTRAINT fk_file_processing_errors_operation
                                                          FOREIGN KEY (file_operation_id) REFERENCES file_operations(id) ON DELETE CASCADE
);

-- Создание таблицы для временного хранения данных импорта
CREATE TABLE IF NOT EXISTS import_staging (
                                              id BIGSERIAL PRIMARY KEY,
                                              operation_id BIGINT NOT NULL,
                                              row_number INTEGER NOT NULL,
                                              row_data JSONB NOT NULL,
                                              status VARCHAR(20) DEFAULT 'PENDING',
                                              error_message TEXT,
                                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ограничения
                                              CONSTRAINT fk_import_staging_operation
                                                  FOREIGN KEY (operation_id) REFERENCES file_operations(id) ON DELETE CASCADE,
                                              CONSTRAINT check_staging_status
                                                  CHECK (status IN ('PENDING', 'PROCESSED', 'ERROR', 'SKIPPED'))
);

-- Добавление индексов для новых таблиц

-- Индексы для field_mapping_templates
CREATE INDEX IF NOT EXISTS idx_mapping_templates_client ON field_mapping_templates(client_id);
CREATE INDEX IF NOT EXISTS idx_mapping_templates_entity_type ON field_mapping_templates(entity_type);
CREATE INDEX IF NOT EXISTS idx_mapping_templates_active ON field_mapping_templates(is_active);
CREATE INDEX IF NOT EXISTS idx_mapping_templates_default ON field_mapping_templates(is_default);

-- Индексы для field_mapping_rules
CREATE INDEX IF NOT EXISTS idx_mapping_rules_template ON field_mapping_rules(template_id);
CREATE INDEX IF NOT EXISTS idx_mapping_rules_order ON field_mapping_rules(template_id, order_index);
CREATE INDEX IF NOT EXISTS idx_mapping_rules_active ON field_mapping_rules(is_active);

-- Индексы для file_operation_stats
CREATE INDEX IF NOT EXISTS idx_operation_stats_operation ON file_operation_stats(file_operation_id);

-- Индексы для file_processing_errors
CREATE INDEX IF NOT EXISTS idx_processing_errors_operation ON file_processing_errors(file_operation_id);
CREATE INDEX IF NOT EXISTS idx_processing_errors_type ON file_processing_errors(error_type);
CREATE INDEX IF NOT EXISTS idx_processing_errors_record ON file_processing_errors(record_number);

-- Индексы для import_staging
CREATE INDEX IF NOT EXISTS idx_import_staging_operation ON import_staging(operation_id);
CREATE INDEX IF NOT EXISTS idx_import_staging_status ON import_staging(status);
CREATE INDEX IF NOT EXISTS idx_import_staging_row ON import_staging(operation_id, row_number);

-- Создание функции для обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Создание триггеров для автоматического обновления updated_at
CREATE TRIGGER update_clients_updated_at BEFORE UPDATE ON clients
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_file_operations_updated_at BEFORE UPDATE ON file_operations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_region_data_updated_at BEFORE UPDATE ON region_data
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_competitor_data_updated_at BEFORE UPDATE ON competitor_data
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_field_mapping_templates_updated_at BEFORE UPDATE ON field_mapping_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_file_operation_stats_updated_at BEFORE UPDATE ON file_operation_stats
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Вставка шаблонов по умолчанию для разных типов сущностей
INSERT INTO field_mapping_templates (name, description, entity_type, file_format, is_default, is_active)
VALUES
    ('Стандартный шаблон для товаров', 'Базовый шаблон импорта товаров', 'Product', 'CSV', true, true),
    ('Стандартный шаблон для конкурентов', 'Базовый шаблон импорта данных конкурентов', 'Competitor', 'CSV', true, true),
    ('Стандартный шаблон для регионов', 'Базовый шаблон импорта региональных данных', 'Region', 'CSV', true, true)
ON CONFLICT DO NOTHING;

-- Добавление правил маппинга для стандартного шаблона товаров
INSERT INTO field_mapping_rules (template_id, csv_header, entity_field, field_type, is_required, order_index)
SELECT
    t.id,
    h.csv_header,
    h.entity_field,
    h.field_type,
    h.is_required,
    h.order_index
FROM field_mapping_templates t
         CROSS JOIN (VALUES
                         ('ID товара', 'productId', 'String', true, 1),
                         ('Модель', 'productName', 'String', true, 2),
                         ('Бренд', 'productBrand', 'String', false, 3),
                         ('Штрихкод', 'productBar', 'String', false, 4),
                         ('Описание', 'productDescription', 'String', false, 5),
                         ('Ссылка', 'productUrl', 'String', false, 6),
                         ('Категория товара 1', 'productCategory1', 'String', false, 7),
                         ('Категория товара 2', 'productCategory2', 'String', false, 8),
                         ('Категория товара 3', 'productCategory3', 'String', false, 9),
                         ('Цена', 'productPrice', 'Double', false, 10),
                         ('Аналог', 'productAnalog', 'String', false, 11)
) AS h(csv_header, entity_field, field_type, is_required, order_index)
WHERE t.name = 'Стандартный шаблон для товаров' AND t.is_default = true
ON CONFLICT DO NOTHING;

-- Обновление существующей схемы для поддержки JSONB в staging таблице
-- (PostgreSQL поддерживает JSONB для эффективного хранения JSON данных)
ALTER TABLE import_staging
    ALTER COLUMN row_data TYPE JSONB USING row_data::JSONB;