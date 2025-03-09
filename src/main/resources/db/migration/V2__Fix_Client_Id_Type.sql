-- Изменяем тип столбца id в таблице clients
ALTER TABLE file_operations DROP CONSTRAINT IF EXISTS file_operations_client_id_fkey;

-- Изменяем тип столбца id в таблице clients с SERIAL на BIGINT
ALTER TABLE clients ALTER COLUMN id TYPE BIGINT;

-- Изменяем последовательность для clients_id_seq (если она существует)
DO $$
    BEGIN
        IF EXISTS (SELECT 1 FROM pg_sequences WHERE schemaname = 'public' AND sequencename = 'clients_id_seq') THEN
            ALTER SEQUENCE clients_id_seq AS BIGINT;
        END IF;
    END$$;

-- Изменяем тип столбца id в таблице file_operations
ALTER TABLE file_operations ALTER COLUMN id TYPE BIGINT;
ALTER TABLE file_operations ALTER COLUMN client_id TYPE BIGINT;

-- Изменяем последовательность для file_operations_id_seq (если она существует)
DO $$
    BEGIN
        IF EXISTS (SELECT 1 FROM pg_sequences WHERE schemaname = 'public' AND sequencename = 'file_operations_id_seq') THEN
            ALTER SEQUENCE file_operations_id_seq AS BIGINT;
        END IF;
    END$$;

-- Восстанавливаем внешний ключ
ALTER TABLE file_operations ADD CONSTRAINT file_operations_client_id_fkey
    FOREIGN KEY (client_id) REFERENCES clients(id);