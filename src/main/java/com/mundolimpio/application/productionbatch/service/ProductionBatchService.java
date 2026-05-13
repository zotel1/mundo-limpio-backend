package com.mundolimpio.application.productionbatch.service;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import com.mundolimpio.application.inventory.service.InventoryService;
import com.mundolimpio.application.product.domain.Product;
import com.mundolimpio.application.product.repository.ProductRepository;
import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchRequest;
import com.mundolimpio.application.productionbatch.dto.ProductionBatchResponse;
import com.mundolimpio.application.productionbatch.exception.ProductionBatchNotFoundException;
import com.mundolimpio.application.productionbatch.mapper.ProductionBatchMapper;
import com.mundolimpio.application.productionbatch.repository.ProductionBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service para la gestión de lotes de producción.
 *
 * Lógica de negocio:
 * 1. Tomar materia prima (bulkProduct)
 * 2. Aplicar conversionRatio para calcular litros producidos
 * 3. Calcular costo unitario basado en materia prima usada
 */
@Service
public class ProductionBatchService {

    private final ProductionBatchRepository repository;
    private final ProductRepository productRepository;
    private final BulkProductRepository bulkProductRepository;
    private final ProductionBatchMapper mapper;
    private final InventoryService inventoryService;

    public ProductionBatchService(ProductionBatchRepository repository,
                                 ProductRepository productRepository,
                                 BulkProductRepository bulkProductRepository,
                                 ProductionBatchMapper mapper,
                                 InventoryService inventoryService) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.bulkProductRepository = bulkProductRepository;
        this.mapper = mapper;
        this.inventoryService = inventoryService;
    }

    /**
     * Crea un nuevo lote de producción.
     *
     * @param request Contiene productId, bulkProductId, rawQuantityUsed
     * @return ProductionBatchResponse con el lote creado
     */
    @Transactional
    public ProductionBatchResponse createProductionBatch(ProductionBatchRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductionBatchNotFoundException("Product not found with id: " + request.productId()));

        BulkProduct bulkProduct = bulkProductRepository.findById(request.bulkProductId())
                .orElseThrow(() -> new ProductionBatchNotFoundException("Bulk product not found with id: " + request.bulkProductId()));

        // Calcular litros producidos: rawQuantityUsed * conversionRatio
        BigDecimal initialQuantity = request.rawQuantityUsed()
                .multiply(bulkProduct.getConversionRatio());

        // Calcular costo unitario: (costo materia prima * cantidad usada) / litros producidos
        BigDecimal totalCost = bulkProduct.getCostPerLiter().multiply(request.rawQuantityUsed());
        BigDecimal unitCost = totalCost.divide(initialQuantity, BigDecimal.ROUND_HALF_UP);

        // Descontar de la materia prima (actualizar stock)
        BigDecimal newStock = bulkProduct.getCurrentStockLiters().subtract(request.rawQuantityUsed());
        bulkProduct.setCurrentStockLiters(newStock);
        bulkProductRepository.save(bulkProduct);

        // Crear el lote
        ProductionBatch batch = mapper.toEntity(
                request, product, bulkProduct,
                initialQuantity, initialQuantity, unitCost
        );

        ProductionBatch saved = repository.save(batch);

        // ===== INVENTORY INTEGRATION =====
        // QUE HACE: Al crear un lote de producción, incrementamos el inventario
        // del producto terminado. La cantidad producida (initialQuantity) se suma
        // al currentStock del Inventory de ese producto.
        //
        // POR QUE: El module de Inventory trackea el stock disponible de cada
        // producto terminado de forma independiente al stock por lotes (FIFO).
        // Cada lote nuevo aumenta el stock total disponible para la venta.
        //
        // DIFERENCIA con el stock de ProductionBatch:
        // - production_batches.current_stock trackea cuánto queda de CADA lote
        //   individualmente (para FIFO).
        // - inventory.current_stock trackea el stock TOTAL del producto (1:1).
        // - Ambos se actualizan en la misma transacción @Transactional para
        //   mantener consistencia: si falla uno, se revierte el otro.
        inventoryService.incrementStock(request.productId(), initialQuantity);

        return mapper.toResponse(saved);
    }

    /**
     * Obtiene todos los lotes de un producto específico.
     */
    public List<ProductionBatchResponse> getBatchesByProductId(Long productId) {
        return repository.findByProductId(productId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Obtiene un lote por ID.
     */
    public ProductionBatchResponse getBatchById(Long id) {
        ProductionBatch batch = repository.findById(id)
                .orElseThrow(() -> new ProductionBatchNotFoundException("Production batch not found with id: " + id));
        return mapper.toResponse(batch);
    }

    /**
     * Obtiene todos los lotes (para uso en FIFO).
     */
    public List<ProductionBatch> findAllWithStockForFifo(Long productId) {
        return repository.findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc(
                productId, BigDecimal.ZERO
        );
    }
}
