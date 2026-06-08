package com.mundolimpio.application.bulkproduct.exception;

import com.mundolimpio.application.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para BulkProductExceptionHandler.
 * <p>
 * WHY: Verifica que el handler retorna ErrorResponse en vez de Map,
 * incluyendo timestamp ISO-8601 y path del request.
 */
class BulkProductExceptionHandlerTest {

    private final BulkProductExceptionHandler handler = new BulkProductExceptionHandler();

    /**
     * RED: handleBulkProductNotFoundException debe retornar 404 con ErrorResponse
     * y código BULK_PRODUCT_NOT_FOUND.
     * Triangulamos con dos mensajes distintos.
     */
    @Test
    void testHandleBulkProductNotFound_Returns404WithErrorResponse() {
        // Given
        BulkProductNotFoundException ex1 = new BulkProductNotFoundException("Bulk product not found with id: 1");
        BulkProductNotFoundException ex2 = new BulkProductNotFoundException("Bulk product 999 does not exist");
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/v1/bulk-products/1");
        WebRequest request = new ServletWebRequest(mockRequest);

        // When
        ResponseEntity<ErrorResponse> response1 = handler.handleBulkProductNotFoundException(ex1, request);
        ResponseEntity<ErrorResponse> response2 = handler.handleBulkProductNotFoundException(ex2, request);

        // Then — status 404
        assertEquals(HttpStatus.NOT_FOUND, response1.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, response2.getStatusCode());

        // Then — body es ErrorResponse con los campos esperados
        ErrorResponse body1 = response1.getBody();
        assertNotNull(body1);
        assertEquals("BULK_PRODUCT_NOT_FOUND", body1.code());
        assertEquals("Bulk product not found with id: 1", body1.message());
        assertNotNull(body1.timestamp());
        assertEquals("/api/v1/bulk-products/1", body1.path());

        // Triangulación — segundo mensaje
        ErrorResponse body2 = response2.getBody();
        assertNotNull(body2);
        assertEquals("BULK_PRODUCT_NOT_FOUND", body2.code());
        assertEquals("Bulk product 999 does not exist", body2.message());
        assertNotNull(body2.timestamp());
    }

    /**
     * RED: El handler debe incluir el path del request en la respuesta.
     */
    @Test
    void testHandleBulkProductNotFound_IncludesPath() {
        // Given
        BulkProductNotFoundException ex = new BulkProductNotFoundException("test error");
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/v1/bulk-products/42");
        WebRequest request = new ServletWebRequest(mockRequest);

        // When
        ResponseEntity<ErrorResponse> response = handler.handleBulkProductNotFoundException(ex, request);

        // Then
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("/api/v1/bulk-products/42", body.path());
    }
}
