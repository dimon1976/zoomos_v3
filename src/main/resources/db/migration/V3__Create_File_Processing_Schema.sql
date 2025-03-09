-- src/main/resources/db/migration/V3__Create_File_Processing_Schema.sql

-- Создание таблицы для конфигураций сопоставления полей
CREATE TABLE field_mappings (
                                id BIGSERIAL PRIMARY KEY,
                                name VARCHAR(255) NOT NULL,
                                description TEXT,
                                client_id BIGINT REFERENCES clients(id),
                                entity_type VARCHAR(100) NOT NULL, -- Тип целевой сущности (например, 'product', 'user', 'order')
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                is_active BOOLEAN DEFAULT TRUE,

                                CONSTRAINT field_mappings_unique_name_client UNIQUE (name, client_id, entity_type)
);

-- Создание таблицы для деталей сопоставления отдельных полей
CREATE TABLE field_mapping_details (
                                       id BIGSERIAL PRIMARY KEY,
                                       field_mapping_id BIGINT NOT NULL REFERENCES field_mappings(id) ON DELETE CASCADE,
                                       source_field VARCHAR(255) NOT NULL, -- Название поля в файле (заголовок)
                                       target_field VARCHAR(255) NOT NULL, -- Название поля в сущности
                                       required BOOLEAN DEFAULT FALSE, -- Является ли поле обязательным
                                       transformation_type VARCHAR(100), -- Тип преобразования (например, 'date', 'number', 'text')
                                       transformation_params TEXT, -- Параметры преобразования в формате JSON
                                       validation_rules TEXT, -- Правила валидации в формате JSON
                                       default_value TEXT, -- Значение по умолчанию
                                       order_index INT DEFAULT 0, -- Порядок обработки поля

                                       CONSTRAINT field_mapping_details_unique_fields UNIQUE (field_mapping_id, source_field)
);

-- Создание таблицы для стратегий обработки файлов
CREATE TABLE processing_strategies (
                                       id BIGSERIAL PRIMARY KEY,
                                       name VARCHAR(255) NOT NULL,
                                       description TEXT,
                                       strategy_type VARCHAR(100) NOT NULL, -- Тип стратегии (например, 'import', 'export', 'validate')
                                       strategy_class VARCHAR(255) NOT NULL, -- Полное имя класса стратегии
                                       parameters TEXT, -- Параметры стратегии в формате JSON
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       is_active BOOLEAN DEFAULT TRUE,

                                       CONSTRAINT processing_strategies_unique_name UNIQUE (name)
);

-- Создание таблицы для статистики и метрик файловых операций
CREATE TABLE file_operation_stats (
                                      id BIGSERIAL PRIMARY KEY,
                                      file_operation_id BIGINT NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
                                      processed_records INT DEFAULT 0, -- Количество обработанных записей
                                      successful_records INT DEFAULT 0, -- Количество успешно обработанных записей
                                      failed_records INT DEFAULT 0, -- Количество записей с ошибками
                                      processing_time_ms BIGINT DEFAULT 0, -- Время обработки в миллисекундах
                                      memory_used_kb INT DEFAULT 0, -- Использованная память в КБ

    -- Детальная статистика
                                      validation_errors INT DEFAULT 0, -- Количество ошибок валидации
                                      transformation_errors INT DEFAULT 0, -- Количество ошибок преобразования
                                      database_errors INT DEFAULT 0, -- Количество ошибок при обращении к БД

    -- Статистика по чанкам
                                      chunk_size INT DEFAULT 0, -- Размер чанка
                                      chunk_count INT DEFAULT 0, -- Количество обработанных чанков

    -- Дополнительная информация
                                      stats_details TEXT, -- Детальная информация о статистике в формате JSON
                                      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Создание таблицы для хранения деталей ошибок обработки
CREATE TABLE file_processing_errors (
                                        id BIGSERIAL PRIMARY KEY,
                                        file_operation_id BIGINT NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
                                        error_type VARCHAR(100) NOT NULL, -- Тип ошибки (например, 'validation', 'transformation', 'database')
                                        error_message TEXT NOT NULL, -- Сообщение об ошибке
                                        error_details TEXT, -- Детальная информация об ошибке в формате JSON
                                        record_number INT, -- Номер записи, в которой произошла ошибка
                                        source_field VARCHAR(255), -- Поле, в котором произошла ошибка
                                        source_value TEXT, -- Значение поля, вызвавшее ошибку
                                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Обновление таблицы file_operations для поддержки новой функциональности
ALTER TABLE file_operations
    ADD COLUMN field_mapping_id BIGINT REFERENCES field_mappings(id),
    ADD COLUMN strategy_id BIGINT REFERENCES processing_strategies(id),
    ADD COLUMN total_records INT DEFAULT 0,
    ADD COLUMN processed_records INT DEFAULT 0,
    ADD COLUMN processing_progress INT DEFAULT 0,
    ADD COLUMN file_hash VARCHAR(64), -- Хеш содержимого файла для проверки дубликатов
    ADD COLUMN file_size BIGINT DEFAULT 0, -- Размер файла в байтах
    ADD COLUMN processing_params TEXT, -- Параметры обработки в формате JSON
    ADD COLUMN source_file_path TEXT, -- Путь к исходному файлу
    ADD COLUMN result_file_path TEXT; -- Путь к файлу с результатами (для экспорта);

-- Индексы
CREATE INDEX idx_field_mappings_client_id ON field_mappings(client_id);
CREATE INDEX idx_field_mapping_details_mapping_id ON field_mapping_details(field_mapping_id);
CREATE INDEX idx_file_operation_stats_operation_id ON file_operation_stats(file_operation_id);
CREATE INDEX idx_file_processing_errors_operation_id ON file_processing_errors(file_operation_id);
CREATE INDEX idx_file_operations_field_mapping_id ON file_operations(field_mapping_id);
CREATE INDEX idx_file_operations_strategy_id ON file_operations(strategy_id);