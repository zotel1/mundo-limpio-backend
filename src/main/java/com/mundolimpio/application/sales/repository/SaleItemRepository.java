package com.mundolimpio.application.sales.repository;

import com.mundolimpio.application.sales.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad SaleItem.
 * 
 * POR QUÉ repositorio separado en vez de acceder vía Sale:
 * - SaleItem se persiste individualmente dentro de la transacción de createSale().
 * - En el futuro, podríamos necesitar queries sobre items específicos (ej: "todos los
 *   items vendidos de un lote en particular") que no requieren cargar la venta completa.
 * - Separación de responsabilidades: cada entidad tiene su propio repositorio.
 * 
 * Actualmente vacío porque las queries básicas las cubre JpaRepository.
 */
@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
}
