package com.mundolimpio.application.productionbatch.repository;

import com.mundolimpio.application.productionbatch.domain.ProductionBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {

    /**
     * Encuentra lotes con stock disponible para un producto (para FIFO).
     * Ordenado por fecha de producción (el más antiguo primero).
     */
    List<ProductionBatch> findByProductIdAndCurrentStockGreaterThanOrderByProductionDateAsc(
            Long productId, BigDecimal minStock);

    /**
     * Obtiene todos los lotes de un producto.
     */
    List<ProductionBatch> findByProductId(Long productId);

    /**
     * Pagination: obtiene lotes de un producto con paginación.
     */
    Page<ProductionBatch> findByProductId(Long productId, Pageable pageable);
}
