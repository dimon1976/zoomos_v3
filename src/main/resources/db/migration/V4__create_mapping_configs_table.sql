-- V4__create_mapping_configs_table.sql
CREATE TABLE mapping_configs (
                                 id BIGSERIAL PRIMARY KEY,
                                 client_id BIGINT NOT NULL REFERENCES clients(id),
                                 name VARCHAR(255) NOT NULL,
                                 description TEXT,
                                 file_type VARCHAR(10) NOT NULL,
                                 product_mapping JSONB,
                                 region_mapping JSONB,
                                 competitor_mapping JSONB,
                                 active BOOLEAN DEFAULT true,
                                 is_default BOOLEAN DEFAULT false,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP,

                                 CONSTRAINT unique_client_default_mapping UNIQUE (client_id, is_default)
);

CREATE INDEX idx_mapping_configs_client ON mapping_configs(client_id);
CREATE INDEX idx_mapping_configs_active ON mapping_configs(active);
CREATE INDEX idx_mapping_configs_file_type ON mapping_configs(file_type);