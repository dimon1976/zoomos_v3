-- V3__Fix_Old_Schema.sql
-- Миграция для удаления старых таблиц и исправления схемы

-- Удаление старых таблиц если они существуют
DROP TABLE IF EXISTS field_mapping_details CASCADE;
DROP TABLE IF EXISTS field_mappings CASCADE;
DROP TABLE IF EXISTS processing_strategies CASCADE;

-- Удаление неиспользуемых колонок из file_operations если они есть
DO $$
    BEGIN
        -- Проверяем и удаляем старые колонки
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'file_operations'
                     AND column_name = 'field_mapping_id') THEN
            ALTER TABLE file_operations DROP COLUMN field_mapping_id;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'file_operations'
                     AND column_name = 'strategy_id') THEN
            ALTER TABLE file_operations DROP COLUMN strategy_id;
        END IF;
    END $$;

-- Добавление новых колонок в file_operations для связи с шаблонами
ALTER TABLE file_operations
    ADD COLUMN IF NOT EXISTS mapping_template_id BIGINT,
    ADD COLUMN IF NOT EXISTS import_entity_type VARCHAR(50);

-- Добавление внешнего ключа
ALTER TABLE file_operations
    ADD CONSTRAINT fk_file_operations_mapping_template
        FOREIGN KEY (mapping_template_id)
            REFERENCES field_mapping_templates(id)
            ON DELETE SET NULL;

-- Создание представления для удобного просмотра статистики операций
CREATE OR REPLACE VIEW v_operation_summary AS
SELECT
    fo.id,
    fo.operation_type,
    fo.status,
    c.name as client_name,
    fo.file_name,
    fo.file_size,
    fo.total_records,
    fo.processed_records,
    fo.processing_progress,
    fo.started_at,
    fo.completed_at,
    CASE
        WHEN fo.completed_at IS NOT NULL
            THEN EXTRACT(EPOCH FROM (fo.completed_at - fo.started_at))
        ELSE NULL
        END as duration_seconds,
    fmt.name as mapping_template_name,
    fo.error_message
FROM file_operations fo
         JOIN clients c ON fo.client_id = c.id
         LEFT JOIN field_mapping_templates fmt ON fo.mapping_template_id = fmt.id
ORDER BY fo.started_at DESC;

-- Создание представления для анализа ошибок
CREATE OR REPLACE VIEW v_error_analysis AS
SELECT
    fpe.file_operation_id,
    fo.file_name,
    c.name as client_name,
    fpe.error_type,
    COUNT(*) as error_count,
    MIN(fpe.record_number) as first_error_row,
    MAX(fpe.record_number) as last_error_row
FROM file_processing_errors fpe
         JOIN file_operations fo ON fpe.file_operation_id = fo.id
         JOIN clients c ON fo.client_id = c.id
GROUP BY fpe.file_operation_id, fo.file_name, c.name, fpe.error_type
ORDER BY error_count DESC;

-- Добавление полезных комментариев к таблицам
COMMENT ON TABLE clients IS 'Клиенты системы';
COMMENT ON TABLE file_operations IS 'Журнал файловых операций (импорт/экспорт)';
COMMENT ON TABLE products IS 'Импортированные товары';
COMMENT ON TABLE region_data IS 'Региональные данные товаров';
COMMENT ON TABLE competitor_data IS 'Данные о конкурентах';
COMMENT ON TABLE field_mapping_templates IS 'Шаблоны сопоставления полей CSV и сущностей';
COMMENT ON TABLE field_mapping_rules IS 'Правила маппинга для отдельных полей';
COMMENT ON TABLE file_operation_stats IS 'Статистика выполнения файловых операций';
COMMENT ON TABLE file_processing_errors IS 'Журнал ошибок обработки файлов';
COMMENT ON TABLE import_staging IS 'Временное хранилище для импортируемых данных';

-- Создание индекса для ускорения поиска по JSONB
CREATE INDEX IF NOT EXISTS idx_import_staging_row_data ON import_staging USING GIN (row_data);