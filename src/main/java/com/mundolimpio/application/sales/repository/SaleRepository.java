package com.mundolimpio.application.sales.repository;

import com.mundolimpio.application.sales.domain.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad Sale.
 * 
 * POR QUÉ JpaRepository en vez de手写 SQL:
 * - JpaRepository provee CRUD básico sin código (save, findById, findAll, delete, etc.).
 * - Spring genera la implementación automáticamente en runtime.
 * - Si necesitamos queries customizadas, las agregamos como métodos con naming conventions
 *   (ej: findByCreatedAtAfter) o con @Query.
 * 
 * Actualmente vacío porque las queries básicas las cubre JpaRepository.
 * Se pueden agregar métodos específicos cuando los necesitemos (ej: findByDateRange).
 */
@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
}
