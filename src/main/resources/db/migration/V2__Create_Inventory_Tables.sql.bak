-- V2__Create_Inventory_Tables.sql
--
-- QUE HACE: Crea las tablas inventory e inventory_adjustments para el módulo
-- de inventario. inventory almacena el stock actual de cada producto (relación 1:1),
-- mientras que inventory_adjustments guarda el historial de ajustes manuales
-- (quiebres, devoluciones, pérdidas de calidad, etc.).
--
-- POR QUE: Product es una entidad de catálogo (SKU, nombre, precio) y no debe
-- tener campos de stock. Separar en inventory/ permite:
--   1. Optimistic locking con @Version propio (sin afectar a Product)
--   2. Auditoría de cambios mediante inventory_adjustments
--   3. Umbral de stock mínimo (min_stock_threshold) para alertas de low-stock
--
-- DIFERENCIA con V1: V1 creó las tablas base (products, production_batches, sales).
-- V2 agrega una capa de inventario que es independiente: no modifica tablas existentes,
-- solo crea nuevas con FK hacia products.

CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    current_stock DECIMAL(19,4) NOT NULL DEFAULT 0,
    min_stock_threshold DECIMAL(19,4) NOT NULL DEFAULT 0,
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE RESTRICT
);

CREATE TABLE inventory_adjustments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adjustment_inventory FOREIGN KEY (inventory_id)
        REFERENCES inventory(id) ON DELETE RESTRICT
);

-- Indice para consultar ajustes por inventario ordenados por fecha
CREATE INDEX idx_adjustment_inventory_id ON inventory_adjustments(inventory_id);

-- Seed: Crea una fila en inventory para cada producto existente.
-- Usa LEFT JOIN para incluir productos sin lotes de producción (current_stock = 0).
-- POR QUE: REQ-6 exige que todos los productos tengan inventory al iniciar.
-- DIFERENCIA con simplemente dejar que el servicio cree la fila al primer request:
--   - Hacerlo en la migración evita lógica condicional en el GET
--   - La data es consistente desde el inicio de la aplicación
--   - LEFT JOIN asegura que productos sin batches también se incluyan
INSERT INTO inventory (product_id, current_stock, min_stock_threshold)
SELECT p.id, COALESCE(SUM(pb.current_stock), 0), 0
FROM products p
LEFT JOIN production_batches pb ON pb.product_id = p.id
GROUP BY p.id, p.sku;
