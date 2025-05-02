-- Исправленная SQL миграция V6__Update_file_operations.sql

-- Таблица для этапов операции
CREATE TABLE IF NOT EXISTS file_operation_stages (
                                                     operation_id BIGINT NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
                                                     stage_order INT NOT NULL,
                                                     name VARCHAR(50) NOT NULL,
                                                     description VARCHAR(255),
                                                     progress INT DEFAULT 0,
                                                     started_at TIMESTAMP WITH TIME ZONE,
                                                     completed_at TIMESTAMP WITH TIME ZONE,
                                                     status VARCHAR(20) NOT NULL, -- оставляем как VARCHAR
                                                     PRIMARY KEY (operation_id, stage_order)
);

-- Таблица для шаблонов экспорта
CREATE TABLE IF NOT EXISTS export_templates (
                                                id BIGSERIAL PRIMARY KEY,
                                                name VARCHAR(255) NOT NULL,
                                                description TEXT,
                                                client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
                                                entity_type VARCHAR(100) NOT NULL,
                                                fields_json TEXT NOT NULL,
                                                options_json TEXT NOT NULL,
                                                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                                updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                                is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Создание условного ограничения уникальности
CREATE UNIQUE INDEX idx_unique_active_template
    ON export_templates (name, client_id, entity_type)
    WHERE is_active = true;

-- Индексы
CREATE INDEX idx_export_templates_client_id ON export_templates(client_id);
CREATE INDEX idx_export_templates_entity_type ON export_templates(entity_type);
CREATE INDEX idx_file_operation_stages_operation_id ON file_operation_stages(operation_id);