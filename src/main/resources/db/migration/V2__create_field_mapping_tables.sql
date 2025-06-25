-- V2__create_field_mapping_tables.sql
CREATE TABLE IF NOT EXISTS field_mapping_templates (
                                                       id BIGSERIAL PRIMARY KEY,
                                                       name VARCHAR(255) NOT NULL,
                                                       description TEXT,
                                                       client_id BIGINT REFERENCES clients(id),
                                                       entity_type VARCHAR(50) NOT NULL,
                                                       file_format VARCHAR(20),
                                                       is_active BOOLEAN DEFAULT true,
                                                       is_default BOOLEAN DEFAULT false,
                                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                                       UNIQUE(client_id, name)
);

CREATE TABLE IF NOT EXISTS field_mapping_rules (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   template_id BIGINT NOT NULL REFERENCES field_mapping_templates(id) ON DELETE CASCADE,
                                                   csv_header VARCHAR(255) NOT NULL,
                                                   entity_field VARCHAR(255) NOT NULL,
                                                   entity_type VARCHAR(50),
                                                   field_type VARCHAR(50),
                                                   transformation_params TEXT,
                                                   is_required BOOLEAN DEFAULT false,
                                                   default_value VARCHAR(255),
                                                   validation_rules TEXT,
                                                   order_index INTEGER DEFAULT 0,
                                                   is_active BOOLEAN DEFAULT true
);

CREATE INDEX idx_template_client ON field_mapping_templates(client_id);
CREATE INDEX idx_rule_template ON field_mapping_rules(template_id);