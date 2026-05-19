package com.mundolimpio.application.receipt.repository;

import com.mundolimpio.application.receipt.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio Spring Data JPA para la entidad Supplier.
 * 
 * POR QUÉ findByName:
 * - En el flujo de confirmación (ReceiptConfirmationService), necesitamos buscar
 *   un proveedor por su nombre para hacer find-or-create.
 * - Spring Data genera la query automáticamente a partir del nombre del método.
 */
@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * Busca un proveedor por su nombre exacto.
     * @param name Nombre del proveedor (case-sensitive)
     * @return Optional con el Supplier si existe
     */
    Optional<Supplier> findByName(String name);
}
