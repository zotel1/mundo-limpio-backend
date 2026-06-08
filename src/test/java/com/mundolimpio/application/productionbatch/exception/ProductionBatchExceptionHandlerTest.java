package com.mundolimpio.application.productionbatch.exception;

import com.mundolimpio.application.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para ProductionBatchExceptionHandler.
 * <p>
 * WHY: Verifica que el handler retorna ErrorResponse en vez de Map,
 * incluyendo timestamp ISO-8601 y path del request.
 */
class ProductionBatchExceptionHandlerTest {

    private final ProductionBatchExceptionHandler handler = new ProductionBatchExceptionHandler();

    /**
     * RED: handleProductionBatchNotFound debe retornar 404 con ErrorResponse
     * y código PRODUCTION_BATCH_NOT_FOUND.
     * Triangulamos con dos mensajes distintos.
     */
    @Test
    void testHandleProductionBatchNotFound_Returns404WithErrorResponse() {
        // Given
        ProductionBatchNotFoundException ex1 = new ProductionBatchNotFoundException("Batch not found with id: 1");
        ProductionBatchNotFoundException ex2 = new ProductionBatchNotFoundException("Production batch 999 does not exist");
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/v1/production-batches/1");
        WebRequest request = new ServletWebRequest(mockRequest);

        // When
        ResponseEntity<ErrorResponse> response1 = handler.handleProductionBatchNotFound(ex1, request);
        ResponseEntity<ErrorResponse> response2 = handler.handleProductionBatchNotFound(ex2, request);

        // Then — status 404
        assertEquals(HttpStatus.NOT_FOUND, response1.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, response2.getStatusCode());

        // Then — body es ErrorResponse con los campos esperados
        ErrorResponse body1 = response1.getBody();
        assertNotNull(body1);
        assertEquals("PRODUCTION_BATCH_NOT_FOUND", body1.code());
        assertEquals("Batch not found with id: 1", body1.message());
        assertNotNull(body1.timestamp());
        assertEquals("/api/v1/production-batches/1", body1.path());

        ErrorResponse body2 = response2.getBody();
        assertNotNull(body2);
        assertEquals("PRODUCTION_BATCH_NOT_FOUND", body2.code());
        assertEquals("Production batch 999 does not exist", body2.message());
        assertNotNull(body2.timestamp());
    }

    /**
     * RED: El handler debe incluir el path del request en la respuesta.
     */
    @Test
    void testHandleProductionBatchNotFound_IncludesPath() {
        // Given
        ProductionBatchNotFoundException ex = new ProductionBatchNotFoundException("test error");
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/v1/production-batches/42");
        WebRequest request = new ServletWebRequest(mockRequest);

        // When
        ResponseEntity<ErrorResponse> response = handler.handleProductionBatchNotFound(ex, request);

        // Then
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("/api/v1/production-batches/42", body.path());
    }
}
