package com.mundolimpio.application.sales.mapper;

import com.mundolimpio.application.sales.domain.Sale;
import com.mundolimpio.application.sales.domain.SaleItem;
import com.mundolimpio.application.sales.dto.SaleRequest;
import com.mundolimpio.application.sales.dto.SaleResponse;
import com.mundolimpio.application.sales.dto.SaleItemResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper que convierte entre entidades de dominio y DTOs (Data Transfer Objects).
 * 
 * POR QUÉ ESTA IMPLEMENTACIÓN:
 * - Usamos un mapper manual (en vez de MapStruct o similar) porque la lógica de
 *   conversión es simple y no vale la pena la dependencia adicional.
 * - @Component permite inyección automática por Spring (lo usamos en SaleService).
 * - Los DTOs separan la API del dominio: el cliente nunca ve las entidades JPA
 *   ni sus anotaciones. Si cambiamos la base de datos, la API no se rompe.
 * - toEntity() está parcialmente implementado porque en nuestro flujo real,
 *   la entidad Sale se crea directamente en SaleService con los datos FIFO
 *   (no desde el request). Este método queda como placeholder.
 */
@Component
public class SaleMapper {

    /**
     * Convierte un request de creación a entidad Sale.
     * NOTA: Este método NO se usa en el flujo actual porque SaleService crea la
     * entidad manualmente con el totalAmount calculado vía FIFO.
     * Se mantiene como placeholder para futuros usos (ej: importación masiva).
     */
    public Sale toEntity(SaleRequest request) {
        // Implementación mínima para fase GREEN
        // Se mejorará en Phase 3 con lógica FIFO completa
        return new Sale(request.quantity()); // Temporal: usa quantity como totalAmount
    }

    /**
     * Convierte una entidad Sale a SaleResponse (DTO de salida).
     * Este es el método más usado: después de crear una venta, retornamos el
     * response al cliente con todos los detalles.
     * 
     * POR QUÉ stream + collect: Convertimos cada SaleItem a SaleItemResponse
     * de forma funcional. Es más legible que un loop imperativo.
     */
    public SaleResponse toResponse(Sale sale) {
        List<SaleItemResponse> itemResponses = sale.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return new SaleResponse(
                sale.getId(),
                sale.getTotalAmount(),
                sale.getCreatedAt(),
                itemResponses
        );
    }

    /**
     * Convierte un SaleItem individual a SaleItemResponse.
     * private porque solo se usa internamente dentro del stream de toResponse().
     * DIFFERENCES: Antes convertia Integer a BigDecimal via new BigDecimal(). Ahora
     * item.getQuantity() ya devuelve BigDecimal directo (SaleItem.quantity cambio a BigDecimal).
     */
    private SaleItemResponse toItemResponse(SaleItem item) {
        return new SaleItemResponse(
                item.getProductionBatchId(),
                item.getQuantity(),
                item.getUnitPriceAtSale(),
                item.getUnitCostAtSale()
        );
    }
}
