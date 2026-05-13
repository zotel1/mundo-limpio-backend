package com.mundolimpio.application.inventory.mapper;

import com.mundolimpio.application.inventory.domain.Inventory;
import com.mundolimpio.application.inventory.dto.InventoryResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper para convertir entre entidades Inventory y DTOs.
 *
 * QUE HACE: Centraliza la conversión de Inventory (entidad JPA) a
 * InventoryResponse (DTO de respuesta). Si en el futuro se necesitan
 * más conversiones (ej: de request a entity), se agregan aquí.
 *
 * POR QUE: Separar la lógica de conversión del servicio mantiene cada
 * clase con una única responsabilidad. El servicio se enfoca en reglas
 * de negocio, el mapper se enfoca en transformación de datos.
 *
 * DIFERENCIA con ProductMapper:
 *   - ProductMapper tiene toEntity(ProductRequest) Y toResponse(Product)
 *     porque maneja tanto creación como consulta de productos.
 *   - InventoryMapper por ahora solo tiene toResponse(Inventory) porque
 *     los ajustes de stock no se crean desde un DTO de inventario (se
 *     crean desde AdjustmentRequest en el servicio directamente).
 *   - Ambos son @Component (no MapStruct) para mantener consistencia
 *     con el patrón existente del proyecto.
 */
@Component
public class InventoryMapper {

    /**
     * Convierte una entidad Inventory a un DTO InventoryResponse.
     * Obtiene el productId y productName desde la relación @ManyToOne
     * con Product.
     *
     * @param inventory La entidad Inventory desde la base de datos
     * @return InventoryResponse listo para enviar al cliente
     */
    public InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProduct().getId(),
                inventory.getProduct().getName(),
                inventory.getCurrentStock(),
                inventory.getMinStockThreshold()
        );
    }
}
