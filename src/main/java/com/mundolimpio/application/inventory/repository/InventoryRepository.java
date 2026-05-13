package com.mundolimpio.application.inventory.repository;

import com.mundolimpio.application.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
 *     del producto asociado) y findLowStockInventories() (consulta
 *     específica para low-stock alert usando @Query).
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
     * de su propio umbral mínimo. Usado para la funcionalidad de
     * low-stock alert.
     *
     * POR QUE @Query en vez de method derivation:
     * - Spring Data JPA no puede derivar "currentStock < minStockThreshold"
     *   (comparación entre dos columnas de la misma entidad) desde
     *   el nombre del método.
     * - findByCurrentStockLessThan(BigDecimal) compara contra un valor
     *   fijo, no contra otra columna.
     * - @Query con JPQL permite la comparación directa entre columnas.
     *
     * @return Lista de inventarios con stock por debajo del umbral
     */
    @Query("SELECT i FROM Inventory i WHERE i.currentStock < i.minStockThreshold")
    List<Inventory> findLowStockInventories();
}
