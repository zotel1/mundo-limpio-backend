package com.mundolimpio.application.bulkproduct.service;

import com.mundolimpio.application.bulkproduct.domain.BulkProduct;
import com.mundolimpio.application.bulkproduct.dto.BulkProductResponse;
import com.mundolimpio.application.bulkproduct.exception.BulkProductNotFoundException;
import com.mundolimpio.application.bulkproduct.mapper.BulkProductMapper;
import com.mundolimpio.application.bulkproduct.repository.BulkProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para BulkProductService usando Mockito.
 *
 * WHAT: Verifica la lógica de soft delete, reactivate y filtrado de materias primas
 *       aislado de sus dependencias (repositorio, mapper).
 * WHY: Mockito es más rápido que @SpringBootTest — no levanta contexto Spring.
 *      Sigue el patrón de InventoryServiceTest y ProductionBatchServiceTest.
 * DIFFERENCES: Los tests de soft delete son análogos a ProductService (que no tiene
 *              tests unitarios propios, pero la lógica es idéntica).
 */
@ExtendWith(MockitoExtension.class)
class BulkProductServiceTest {

    @Mock
    private BulkProductRepository repository;

    @Mock
    private BulkProductMapper mapper;

    @InjectMocks
    private BulkProductService service;

    private BulkProduct activeEntity;
    private BulkProduct inactiveEntity;
    private BulkProductResponse activeResponse;
    private BulkProductResponse inactiveResponse;

    @BeforeEach
    void setUp() {
        activeEntity = new BulkProduct(1L, "Cloro Puro", new BigDecimal("20.00"),
                new BigDecimal("5.50"), new BigDecimal("4.0"));
        activeEntity.setActive(true);

        inactiveEntity = new BulkProduct(2L, "Detergente Base", new BigDecimal("15.00"),
                new BigDecimal("8.00"), new BigDecimal("3.0"));
        inactiveEntity.setActive(false);

        activeResponse = new BulkProductResponse(1L, "Cloro Puro", new BigDecimal("20.00"),
                new BigDecimal("5.50"), new BigDecimal("4.0"), true);

        inactiveResponse = new BulkProductResponse(2L, "Detergente Base", new BigDecimal("15.00"),
                new BigDecimal("8.00"), new BigDecimal("3.0"), false);
    }

    // ==================== SOFT DELETE ====================

    @Test
    void shouldSoftDeleteBulkProduct() {
        // GIVEN: un bulk product activo existe
        when(repository.findById(1L)).thenReturn(Optional.of(activeEntity));
        when(repository.save(any(BulkProduct.class))).thenReturn(activeEntity);

        // WHEN: se ejecuta el soft delete
        service.deleteBulkProduct(1L);

        // THEN: active se marca como false y se persiste
        assertFalse(activeEntity.getActive());
        verify(repository).findById(1L);
        verify(repository).save(activeEntity);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void shouldThrowNotFoundOnDeleteNonexistent() {
        // GIVEN: no existe un bulk product con ese ID
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // WHEN & THEN: debe lanzar BulkProductNotFoundException
        BulkProductNotFoundException exception = assertThrows(
                BulkProductNotFoundException.class,
                () -> service.deleteBulkProduct(999L)
        );

        assertTrue(exception.getMessage().contains("999"));
        verify(repository).findById(999L);
        verify(repository, never()).save(any());
    }

    // ==================== REACTIVATE ====================

    @Test
    void shouldReactivateBulkProduct() {
        // GIVEN: un bulk product inactivo existe
        when(repository.findById(2L)).thenReturn(Optional.of(inactiveEntity));
        when(repository.save(any(BulkProduct.class))).thenReturn(inactiveEntity);

        // WHEN: se reactiva
        service.reactivateBulkProduct(2L);

        // THEN: active se marca como true y se persiste
        assertTrue(inactiveEntity.getActive());
        verify(repository).findById(2L);
        verify(repository).save(inactiveEntity);
    }

    @Test
    void shouldReactivateAlreadyActiveProduct() {
        // GIVEN: un bulk product ya está activo
        when(repository.findById(1L)).thenReturn(Optional.of(activeEntity));
        when(repository.save(any(BulkProduct.class))).thenReturn(activeEntity);

        // WHEN: se reactiva (operación idempotente)
        service.reactivateBulkProduct(1L);

        // THEN: sigue activo, sin errores
        assertTrue(activeEntity.getActive());
        verify(repository).findById(1L);
        verify(repository).save(activeEntity);
    }

    @Test
    void shouldThrowNotFoundOnReactivateNonexistent() {
        // GIVEN: no existe un bulk product con ese ID
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // WHEN & THEN: debe lanzar BulkProductNotFoundException
        BulkProductNotFoundException exception = assertThrows(
                BulkProductNotFoundException.class,
                () -> service.reactivateBulkProduct(999L)
        );

        assertTrue(exception.getMessage().contains("999"));
        verify(repository).findById(999L);
        verify(repository, never()).save(any());
    }

    // ==================== QUERY FILTERING ====================

    @Test
    void shouldFilterInactiveFromGetAll() {
        // GIVEN: existen 2 productos activos y 1 inactivo, pero findByActiveTrue solo retorna activos
        Pageable pageable = PageRequest.of(0, 20);
        Page<BulkProduct> activePage = new PageImpl<>(List.of(activeEntity), pageable, 1);
        when(repository.findByActiveTrue(any(Pageable.class))).thenReturn(activePage);
        when(mapper.toResponse(activeEntity)).thenReturn(activeResponse);

        // WHEN: se consultan todas las materias primas activas
        Page<BulkProductResponse> result = service.getAllBulkProducts(pageable);

        // THEN: solo retorna las activas (1 en este caso)
        assertEquals(1, result.getContent().size());
        assertTrue(result.getContent().get(0).active());
        verify(repository).findByActiveTrue(any(Pageable.class));
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldReturnAllIncludingInactiveFromAdmin() {
        // GIVEN: existen productos activos e inactivos
        Pageable pageable = PageRequest.of(0, 20);
        Page<BulkProduct> allPage = new PageImpl<>(List.of(activeEntity, inactiveEntity), pageable, 2);
        when(repository.findAll(any(Pageable.class))).thenReturn(allPage);
        when(mapper.toResponse(activeEntity)).thenReturn(activeResponse);
        when(mapper.toResponse(inactiveEntity)).thenReturn(inactiveResponse);

        // WHEN: se consulta el endpoint admin que retorna todo
        Page<BulkProductResponse> result = service.getAllBulkProductsAdmin(pageable);

        // THEN: retorna todos (2)
        assertEquals(2, result.getContent().size());
        verify(repository).findAll(any(Pageable.class));
        verify(repository, never()).findByActiveTrue(any(Pageable.class));
    }
}
