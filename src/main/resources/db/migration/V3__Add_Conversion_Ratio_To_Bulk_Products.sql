-- V3__Add_Conversion_Ratio_To_Bulk_Products.sql

-- Agregamos la columna conversion_ratio para almacenar el ratio de dilución
-- Ejemplo: Cloro 1:4 → ratio = 4, Detergente 1:3 → ratio = 3
ALTER TABLE bulk_products ADD COLUMN conversion_ratio DECIMAL(19,4) NOT NULL DEFAULT 1.0;

-- Actualizamos los registros existentes (si los hay) a ratio 1 por defecto
-- Si ya tenés productos cargados, deberías actualizarlos manualmente según corresponda
