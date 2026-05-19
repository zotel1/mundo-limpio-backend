package com.mundolimpio.application.receipt.repository;

import com.mundolimpio.application.receipt.domain.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad Purchase.
 * 
 * POR QUÉ JpaRepository:
 * - CRUD básico sin código (save, findById, findAll, delete).
 * - Spring genera la implementación automáticamente.
 * - Métodos custom se agregan cuando se necesiten (ej: findBySupplier, findByStatus).
 */
@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
}
