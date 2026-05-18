-- V1__Initial_Schema.sql

CREATE TABLE bulk_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    current_stock_liters DECIMAL(19,4) NOT NULL DEFAULT 0,
    cost_per_liter DECIMAL(19,4) NOT NULL,
    version BIGINT DEFAULT 0
);

CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    min_price DECIMAL(19,4) NOT NULL,
    active BOOLEAN DEFAULT TRUE
);

CREATE TABLE production_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    initial_quantity DECIMAL(19,4) NOT NULL,
    current_stock DECIMAL(19,4) NOT NULL,
    unit_cost_at_production DECIMAL(19,4) NOT NULL,
    production_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

-- Índice compuesto optimizado para el motor FIFO
CREATE INDEX idx_product_fifo ON production_batches(product_id, production_date);

CREATE TABLE sales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    total_amount DECIMAL(19,4) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sale_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    production_batch_id BIGINT NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    unit_price_at_sale DECIMAL(19,4) NOT NULL,
    unit_cost_at_sale DECIMAL(19,4) NOT NULL,
    CONSTRAINT fk_item_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT fk_item_batch FOREIGN KEY (production_batch_id) REFERENCES production_batches(id)
);