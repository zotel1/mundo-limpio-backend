package com.mundolimpio.application.bulkproduct.service;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.dto.BulkProductRequest;
import com.mundolimpio.application.bulkproduct.dto.BulkProductResponse;
import com.mundolimpio.application.bulkproduct.exception.BulkProductNotFoundException;
import com.mundolimpio.application.bulkproduct.mapper.BulkProductMapper;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service para la gestión de materia prima (bulk products).
 *
 * Contiene la lógica de negocio para CRUD de materia prima.
 */
@Service
public class BulkProductService {

    private final BulkProductRepository repository;
    private final BulkProductMapper mapper;

    public BulkProductService(BulkProductRepository repository, BulkProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Crea una nueva materia prima.
     */
    public BulkProductResponse createBulkProduct(BulkProductRequest request) {
        BulkProduct entity = mapper.toEntity(request);
        BulkProduct saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    /**
     * Obtiene todas las materias primas.
     */
    public List<BulkProductResponse> getAllBulkProducts() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Obtiene una materia prima por ID.
     * @throws NoSuchElementException si no existe
     */
    public BulkProductResponse getBulkProductById(Long id) {
        BulkProduct entity = repository.findById(id)
                .orElseThrow(() -> new BulkProductNotFoundException("Bulk product not found with id: " + id));
        return mapper.toResponse(entity);
    }

    /**
     * Actualiza una materia prima existente.
     */
    public BulkProductResponse updateBulkProduct(Long id, BulkProductRequest request) {
        BulkProduct entity = repository.findById(id)
                .orElseThrow(() -> new BulkProductNotFoundException("Bulk product not found with id: " + id));
        mapper.updateEntityFromRequest(entity, request);
        BulkProduct updated = repository.save(entity);
        return mapper.toResponse(updated);
    }

    /**
     * Elimina una materia prima.
     * Nota: Solo debería usarse si no tiene lotes de producción asociados.
     */
    public void deleteBulkProduct(Long id) {
        if (!repository.existsById(id)) {
            throw new BulkProductNotFoundException("Bulk product not found with id: " + id);
        }
        repository.deleteById(id);
    }
}
