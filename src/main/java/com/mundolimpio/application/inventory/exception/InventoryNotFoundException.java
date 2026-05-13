package com.mundolimpio.application.inventory.exception;

/**
 * Excepción lanzada cuando no se encuentra el inventario de un producto.
 *
 * QUE HACE: Se lanza cuando se consulta el inventario de un productId
 * que no tiene una fila asociada en la tabla inventory.
 *
 * POR QUE: Aunque la migración V2 crea inventario para todos los productos
 * existentes, puede ocurrir que:
 *   1. Un producto se elimine o nunca se haya creado su inventory.
 *   2. Se consulte un productId que no existe en la tabla products.
 * En lugar de devolver un Optional.empty() genérico, esta excepción da
 * un mensaje claro y se traduce a HTTP 404 en GlobalExceptionHandler.
 *
 * DIFERENCIA con ProductNotFoundException:
 *   - ProductNotFoundException busca por SKU y usa mensaje
 *     "Product with SKU '...' not found".
 *   - InventoryNotFoundException busca por productId (Long) y usa
 *     mensaje "Inventory not found for product ID: ...".
 *   - Ambas extienden RuntimeException y serán manejadas por
 *     GlobalExceptionHandler en la fase PR #2.
 */
public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(Long productId) {
        super("Inventory not found for product ID: " + productId);
    }
}
