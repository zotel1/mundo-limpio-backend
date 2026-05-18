-- V4__Update_Production_Batches_Table.sql

-- Agregamos la relación con bulk_products (materia prima usada)
ALTER TABLE production_batches ADD COLUMN bulk_product_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE production_batches ADD CONSTRAINT fk_batch_bulk_product FOREIGN KEY (bulk_product_id) REFERENCES bulk_products(id) ON DELETE RESTRICT;

-- Agregamos cuánta materia prima se usó (en litros)
ALTER TABLE production_batches ADD COLUMN raw_quantity_used DECIMAL(19,4) NOT NULL DEFAULT 0;

-- Índice para consultas por bulk_product
CREATE INDEX idx_batch_bulk_product ON production_batches(bulk_product_id);
