package com.mundolimpio.application.receipt.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para las excepciones y handler del módulo receipt.
 * 
 * POR QUÉ tests unitarios puros (sin Spring context):
 * - OcrProcessingException es una RuntimeException simple.
 * - ReceiptExceptionHandler solo mapea excepciones a ResponseEntity — 
 *   no necesita Spring context para probar la lógica de mapeo.
 * - Usamos MockHttpServletRequest para simular el WebRequest sin levantar el contexto.
 */
class ReceiptExceptionHandlerTest {

    private final ReceiptExceptionHandler handler = new ReceiptExceptionHandler();

    /**
     * Test 1.6.1 RED: OcrProcessingException debe almacenar el mensaje.
     * Triangulamos con dos mensajes distintos.
     */
    @Test
    void testOcrProcessingException_StoresMessage() {
        OcrProcessingException ex1 = new OcrProcessingException("OCR failed: no text detected");
        OcrProcessingException ex2 = new OcrProcessingException("Low confidence: 0.1");

        assertEquals("OCR failed: no text detected", ex1.getMessage());
        assertEquals("Low confidence: 0.1", ex2.getMessage());
    }

    /**
     * Test 1.6.2: OcrProcessingException debe ser subclase de RuntimeException.
     */
    @Test
    void testOcrProcessingException_IsRuntimeException() {
        OcrProcessingException ex = new OcrProcessingException("test");
        assertTrue(ex instanceof RuntimeException);
    }

    /**
     * Test 1.6.3 RED: ReceiptExceptionHandler debe retornar 422 y código OCR_PROCESSING_ERROR.
     * Triangulamos con dos mensajes distintos para forzar lógica real.
     */
    @Test
    void testHandleOcrProcessingException_Returns422() {
        // Given
        OcrProcessingException ex1 = new OcrProcessingException("No text detected");
        OcrProcessingException ex2 = new OcrProcessingException("Confidence too low");
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        // When
        ResponseEntity<Map<String, Object>> response1 =
                handler.handleOcrProcessingException(ex1, request);
        ResponseEntity<Map<String, Object>> response2 =
                handler.handleOcrProcessingException(ex2, request);

        // Then — status 422
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response1.getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response2.getStatusCode());

        // Then — body contiene los campos esperados
        Map<String, Object> body1 = response1.getBody();
        assertNotNull(body1);
        assertEquals("OCR_PROCESSING_ERROR", body1.get("code"));
        assertEquals("No text detected", body1.get("message"));
        assertNotNull(body1.get("timestamp"));
        assertNotNull(body1.get("path"));

        Map<String, Object> body2 = response2.getBody();
        assertEquals("OCR_PROCESSING_ERROR", body2.get("code"));
        assertEquals("Confidence too low", body2.get("message"));
    }

    /**
     * Test 1.6.4: El handler debe incluir el path del request en la respuesta.
     */
    @Test
    void testHandleOcrProcessingException_IncludesPath() {
        OcrProcessingException ex = new OcrProcessingException("test error");
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/v1/receipts/process");
        ServletWebRequest request = new ServletWebRequest(mockRequest);

        ResponseEntity<Map<String, Object>> response =
                handler.handleOcrProcessingException(ex, request);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("/api/v1/receipts/process", body.get("path"));
    }
}
