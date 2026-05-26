package com.mundolimpio.application.sales.mapper;

import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.repository.ProductionBatchRepository;
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
 * 
 * DIFFERENCES con PR 1 (HIGH-1):
 * - Ahora inyecta ProductionBatchRepository para resolver productId y productName
 *   desde el ProductionBatch asociado al SaleItem.
 * - N+1 en toItemResponse: cada item resuelve su batch → product por separado.
 *   Aceptable para MVP. Optimizar con JOIN query si hay problemas de performance.
 */
@Component
public class SaleMapper {

    private final ProductionBatchRepository productionBatchRepository;

    /**
     * Constructor con inyección de dependencias.
     * WHY: HIGH-1 — necesitamos resolver el producto desde el batch del SaleItem.
     */
    public SaleMapper(ProductionBatchRepository productionBatchRepository) {
        this.productionBatchRepository = productionBatchRepository;
    }

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
     * 
     * DIFFERENCES con PR 1 (HIGH-1):
     * - Ahora resuelve productId y productName desde ProductionBatch → Product.
     * - N+1: por cada item, hace un lookup de ProductionBatch por ID.
     *   Aceptable para MVP (cantidad de items por venta es pequeña).
     * - Si el batch no existe (data corruption), usa null para no romper la respuesta.
     */
    private SaleItemResponse toItemResponse(SaleItem item) {
        // Resolver productId y productName desde el batch del SaleItem.
        // WHY: HIGH-1 — el listado de ventas necesita mostrar qué producto se vendió.
        Long productId = null;
        String productName = null;
        ProductionBatch batch = productionBatchRepository.findById(item.getProductionBatchId()).orElse(null);
        if (batch != null && batch.getProduct() != null) {
            productId = batch.getProduct().getId();
            productName = batch.getProduct().getName();
        }

        return new SaleItemResponse(
                item.getProductionBatchId(),
                item.getQuantity(),
                item.getUnitPriceAtSale(),
                item.getUnitCostAtSale(),
                productId,      // WHAT: ID del producto resuelto desde el batch
                                // WHY: HIGH-1 — GET /sales necesita mostrar producto
                productName     // WHAT: Nombre del producto resuelto desde el batch
                                // WHY: HIGH-1 — mostrar nombre legible en la respuesta
        );
    }
}
