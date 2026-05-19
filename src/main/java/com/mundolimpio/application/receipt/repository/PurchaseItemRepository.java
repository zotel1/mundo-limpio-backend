package com.mundolimpio.application.receipt.repository;

import com.mundolimpio.application.receipt.domain.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad PurchaseItem.
 * 
 * POR QUÉ repositorio separado:
 * - Aunque PurchaseItem se persiste normalmente vía cascade de Purchase,
 *   tener su propio repositorio permite queries independientes en el futuro
 *   (ej: "todos los items comprados de un BulkProduct específico").
 * - Separación de responsabilidades: cada entidad tiene su repositorio.
 */
@Repository
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
}
