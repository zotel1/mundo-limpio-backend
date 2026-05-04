package com.mundolimpio.application.bulkproduct.service;

import com.mundolimpio.application.bulkproduct.exception.BulkProductNotFoundException;
import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.dto.BulkProductRequest;
import com.mundolimpio.application.bulkproduct.dto.BulkProductResponse;
import com.mundolimpio.application.bulkproduct.mapper.BulkProductMapper;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;

import org.springframework.stereotype.Service;


import java.util.List;

/**
 * Service para le gestion de materia prima (bulk products).
 *
 * Contioene la lógica de negoios para CRUD de materia prima.
 */
@Service
public class BulkProductService {

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
     * Crea una nueva materia prima
     */
    public BulkProductResponse createBulkProduct(BulkProductRequest request) {
        BulkProduct entity = mapper.toEntity(request);
        BulkProduct saved = repository.save(entity);
        // Conversión: Product → ProductResponse
        return mapper.toResponse(saved);
    }

    // ========================= READ =========================

    /**
     * Obtiene todas las materias primas .

     */
    public List<BulkProductResponse> getAllBulkProducts() {

        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Obtiene una materia prima por Id
     * @throws NoSuchElementException si no existe
     * **/

    public BulkProductResponse getBulkProductById(Long id) {
        BulkProduct entity = repository.findById(id)
                .orElseThrow(() -> new BulkProductNotFoundException("Bulk product not found with id: " + id));
        return mapper.toResponse(entity);
    }

    // ========================= UPDATE =========================

    /**
     * Actualiza una materia prima existente.
     *
     */
    public BulkProductResponse updateBulkProduct(Long id, BulkProductRequest request) {
        BulkProduct entity = repository.findById(id)
                .orElseThrow(() -> new BulkProductNotFoundException("Bulk product not found with id: " + id));
        mapper.updateEntityFromRequest(entity, request);
        BulkProduct updated = repository.save(entity);
        return mapper.toResponse(updated);
    }

    // ========================= DELETE =========================

    /**
     * Elimina una materia prima.
     * Solo deberia usarse si no tiene lotes en produccion asociados
     */
    public void deleteBulkProduct(Long id) {
        if (!repository.existsById(id)) {
            throw new BulkProductNotFoundException("Bulk product not found with id: " + id);
        }
        repository.deleteById(id);
    }
}