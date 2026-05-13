package com.mundolimpio.application.inventory.repository;

import com.mundolimpio.application.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad Inventory.
 *
 * QUE HACE: Provee operaciones CRUD para Inventory más consultas
 * específicas del dominio de inventario.
 *
 * POR QUE: Sigue el patrón de los demás repositorios del proyecto
 * (ProductRepository, ProductionBatchRepository, etc.) extendiendo
 * JpaRepository para obtener las operaciones básicas sin implementación.
 *
 * DIFERENCIA con ProductRepository:
 *   - ProductRepository tiene findBySku() y existsBySku() (búsqueda
 *     por identificador de catálogo).
 *   - InventoryRepository tiene findByProductId() (búsqueda por FK
 *     del producto asociado) y findByCurrentStockLessThan() (consulta
 *     específica para low-stock alert).
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Busca el inventario de un producto específico.
     * La relación es 1:1 (unique constraint en product_id),
     * por lo que siempre devuelve 0 o 1 resultado.
     *
     * @param productId ID del producto
     * @return Optional con el Inventory si existe
     */
    Optional<Inventory> findByProductId(Long productId);

    /**
     * Busca todos los inventarios cuyo stock actual está por debajo
     * del umbral mínimo. Usado para la funcionalidad de low-stock alert.
     *
     * @param threshold El valor contra el que comparar current_stock
     * @return Lista de inventarios con stock bajo
     */
    List<Inventory> findByCurrentStockLessThan(BigDecimal threshold);
}
