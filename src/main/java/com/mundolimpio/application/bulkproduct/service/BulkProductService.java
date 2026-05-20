package com.mundolimpio.application.bulkproduct.service;

import com.mundolimpio.application.bulkproduct.exception.BulkProductNotFoundException;
import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.dto.BulkProductRequest;
import com.mundolimpio.application.bulkproduct.dto.BulkProductResponse;
import com.mundolimpio.application.bulkproduct.mapper.BulkProductMapper;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BulkProductService contiene la lógica de negocio para el módulo de materia prima.
 *
 * Responsabilidades:
 * - Validaciones de negocio
 * - Conversión entre DTOs y Entities (delegando a BulkProductMapper)
 * - Transacciones ACID (@Transactional)
 * - Logging de operaciones
 *
 * @Transactional garantiza que todas las operaciones sean atómicas.
 * Si algo falla, todo se revierte automáticamente.
 */
@Service
@Transactional
public class BulkProductService {

    private static final Logger logger = LoggerFactory.getLogger(BulkProductService.class);

    private final BulkProductRepository repository;
    private final BulkProductMapper mapper;

    /**
     * Constructor con inyección de dependencias.
     * repository: acceso a datos
     * mapper: conversión Entity ↔ DTOs
     */
    public BulkProductService(BulkProductRepository repository, BulkProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    // ========================= CREATE =========================

    /**
     * Crea una nueva materia prima.
     *
     * @param request El BulkProductRequest con los datos de la nueva materia prima
     * @return BulkProductResponse con la materia prima creada (incluyendo ID generado)
     */
    public BulkProductResponse createBulkProduct(BulkProductRequest request) {
        logger.info("Creating bulk product with name: {}", request.name());

        BulkProduct entity = mapper.toEntity(request);
        BulkProduct saved = repository.save(entity);

        logger.info("Bulk product created successfully with ID: {} and name: {}", saved.getId(), saved.getName());
        return mapper.toResponse(saved);
    }

    // ========================= READ =========================

    /**
     * Obtiene todas las materias primas activas.
     *
     * @return Lista de BulkProductResponse de materias primas activas
     */
    public List<BulkProductResponse> getAllBulkProducts() {
        logger.info("Fetching all active bulk products");

        List<BulkProductResponse> activeProducts = repository.findByActiveTrue()
                .stream()
                .map(mapper::toResponse)
                .toList();

        logger.info("Found {} active bulk products", activeProducts.size());
        return activeProducts;
    }

    /**
     * Obtiene todas las materias primas (activas e inactivas).
     *
     * @return Lista de todos los BulkProductResponse
     */
    public List<BulkProductResponse> getAllBulkProductsAdmin() {
        logger.info("Fetching all bulk products (active and inactive)");

        List<BulkProductResponse> allProducts = repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();

        logger.info("Found {} total bulk products", allProducts.size());
        return allProducts;
    }

    /**
     * Obtiene una materia prima por su ID.
     *
     * @param id El identificador único de la materia prima
     * @return BulkProductResponse con los datos encontrados
     * @throws BulkProductNotFoundException si el ID no existe
     */
    public BulkProductResponse getBulkProductById(Long id) {
        logger.info("Fetching bulk product with ID: {}", id);

        BulkProduct entity = repository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Bulk product not found with ID: {}", id);
                    return new BulkProductNotFoundException("Bulk product not found with id: " + id);
                });

        logger.info("Bulk product found with ID: {} (name: {})", id, entity.getName());
        return mapper.toResponse(entity);
    }

    // ========================= UPDATE =========================

    /**
     * Actualiza una materia prima existente.
     *
     * @param id El ID de la materia prima a actualizar
     * @param request El BulkProductRequest con los nuevos datos
     * @return BulkProductResponse con la materia prima actualizada
     * @throws BulkProductNotFoundException si el ID no existe
     */
    public BulkProductResponse updateBulkProduct(Long id, BulkProductRequest request) {
        logger.info("Updating bulk product with ID: {}", id);

        BulkProduct entity = repository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to update non-existent bulk product with ID: {}", id);
                    return new BulkProductNotFoundException("Bulk product not found with id: " + id);
                });

        mapper.updateEntityFromRequest(entity, request);
        BulkProduct updated = repository.save(entity);

        logger.info("Bulk product updated successfully. ID: {}, name: {}", id, updated.getName());
        return mapper.toResponse(updated);
    }

    // ========================= DELETE =========================

    /**
     * Realiza un SOFT DELETE de la materia prima (marca como inactivo).
     * No elimina el registro de la BD, solo marca active = false.
     *
     * Razón: Importante para mantener integridad referencial con
     * production_batches (ON DELETE RESTRICT) y purchase_items (ON DELETE SET NULL).
     *
     * @param id El ID de la materia prima a eliminar (soft delete)
     * @throws BulkProductNotFoundException si el ID no existe
     */
    public void deleteBulkProduct(Long id) {
        logger.info("Performing soft delete on bulk product with ID: {}", id);

        BulkProduct entity = repository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to delete non-existent bulk product with ID: {}", id);
                    return new BulkProductNotFoundException("Bulk product not found with id: " + id);
                });

        entity.setActive(false);
        repository.save(entity);

        logger.info("Bulk product soft deleted (marked as inactive). ID: {}, name: {}", id, entity.getName());
    }

    /**
     * Reactiva una materia prima marcada como inactiva.
     * Reversa de un soft delete.
     *
     * @param id El ID de la materia prima a reactivar
     * @throws BulkProductNotFoundException si el ID no existe
     */
    public void reactivateBulkProduct(Long id) {
        logger.info("Reactivating bulk product with ID: {}", id);

        BulkProduct entity = repository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Attempted to reactivate non-existent bulk product with ID: {}", id);
                    return new BulkProductNotFoundException("Bulk product not found with id: " + id);
                });

        entity.setActive(true);
        repository.save(entity);

        logger.info("Bulk product reactivated. ID: {}, name: {}", id, entity.getName());
    }
}
