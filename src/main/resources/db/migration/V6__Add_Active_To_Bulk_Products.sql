-- V6__Add_Active_To_Bulk_Products.sql
-- WHAT: Agrega columna active a bulk_products para soportar soft delete.
-- WHY: El DELETE actual es un hard delete que puede violar FKs en
--      production_batches (ON DELETE RESTRICT) y purchase_items (ON DELETE SET NULL).
-- DIFFERENCES: V5 creó suppliers/purchases. V6 es una migración aditiva (no destructiva).

ALTER TABLE bulk_products ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
