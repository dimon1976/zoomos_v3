-- Обновление схемы для оптимизации хранения ExportTemplate
-- Удаляем избыточные поля, оставляем только необходимые
ALTER TABLE export_templates
    DROP COLUMN IF EXISTS fields_json,
    DROP COLUMN IF EXISTS file_options;

-- Примечание: если поля не пустые, нужно сначала перенести данные в options_json
-- Если БД уже содержит данные, лучше использовать более сложную миграцию
