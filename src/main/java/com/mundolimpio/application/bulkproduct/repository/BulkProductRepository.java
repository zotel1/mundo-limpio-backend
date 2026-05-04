package com.mundolimpio.application.bulkproduct.repository;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository para la entidad BulkProduct
 * Extiende JpaRepository para obtener métodos CRUD basicos.
 * */

@Repository
public interface BulkProductRepository extends JpaRepository<BulkProduct, Long> {
}