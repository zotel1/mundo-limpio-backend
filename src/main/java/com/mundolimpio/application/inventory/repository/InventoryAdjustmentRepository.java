package com.mundolimpio.application.inventory.repository;

import com.mundolimpio.application.inventory.domain.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para la entidad InventoryAdjustment.
 *
 * QUE HACE: Persiste y consulta los ajustes de inventario (audit trail).
 *
 * POR QUE: Es un repositorio separado de InventoryRepository porque
 * InventoryAdjustment es una entidad independiente con su propio ciclo
 * de vida y consultas específicas. Mezclarlos en un mismo repositorio
 * violaría el principio de responsabilidad única.
 *
 * DIFERENCIA con InventoryRepository:
 *   - InventoryRepository opera sobre el stock actual (estado presente).
 *   - InventoryAdjustmentRepository opera sobre el historial de cambios
 *     (estado pasado). La consulta principal es por inventory_id ordenada
 *     por fecha descendente para mostrar el trail de auditoría.
 */
@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    /**
     * Obtiene todos los ajustes para un inventario, ordenados del más
     * reciente al más antiguo. Usado para mostrar el trail de auditoría.
     *
     * @param inventoryId ID del inventario
     * @return Lista de ajustes ordenada por created_at descendente
     */
    List<InventoryAdjustment> findByInventoryIdOrderByCreatedAtDesc(Long inventoryId);
}
