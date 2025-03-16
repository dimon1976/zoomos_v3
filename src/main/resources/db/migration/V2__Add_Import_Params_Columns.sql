-- For Flyway, create a file like V2__Add_Import_Params_Columns.sql
ALTER TABLE file_operations ADD COLUMN batch_size INT;
ALTER TABLE file_operations ADD COLUMN processing_strategy VARCHAR(50);
ALTER TABLE file_operations ADD COLUMN error_handling VARCHAR(50);
ALTER TABLE file_operations ADD COLUMN duplicate_handling VARCHAR(50);