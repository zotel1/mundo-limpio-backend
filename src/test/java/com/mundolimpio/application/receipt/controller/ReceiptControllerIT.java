package com.mundolimpio.application.receipt.controller;

import com.mundolimpio.application.receipt.dto.*;
import com.mundolimpio.application.receipt.exception.OcrProcessingException;
import com.mundolimpio.application.receipt.service.ReceiptConfirmationService;
import com.mundolimpio.application.receipt.service.ReceiptProcessingService;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.repository.UserRepository;
import com.mundolimpio.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WHAT: Tests de integración para ReceiptController.
 * WHY: Verifica el flujo HTTP completo para ambos endpoints:
 *      POST /api/v1/receipts/process (OCR) y POST /api/v1/receipts/confirm (persistencia).
 *
 * DIFFERENCES: Usamos @AutoConfigureMockMvc para que Spring configure MockMvc
 *              con todos los filtros de seguridad y @ControllerAdvice correctamente.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReceiptControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptProcessingService processingService;

    @MockBean
    private ReceiptConfirmationService confirmationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String operatorToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Crear admin y generar JWT
        User admin = new User("admin_test", "$2a$10$encodedPassword", Role.ADMIN);
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);

        // Crear operator para test de forbidden
        User operator = new User("operator_test", "$2a$10$encodedPassword", Role.OPERATOR);
        userRepository.save(operator);
        operatorToken = jwtService.generateToken(operator);
    }

    // ===================== Tests de procesamiento (process) =====================

    /**
     * RED: Verifica que POST /api/v1/receipts/process retorna 200
     * con ReceiptProcessResponse cuando el OCR es exitoso.
     */
    @Test
    void shouldReturn200WithProcessResponse() throws Exception {
        ReceiptProcessResponse mockResponse = new ReceiptProcessResponse(
                "PROVEEDOR TEST",
                "15/05/2026",
                List.of(new ProductLineDto("Cloro 5L", 2,
                        new BigDecimal("1250.50"), 0.95, null)),
                "https://test.supabase.co/storage/v1/object/public/receipts/img.jpg"
        );

        when(processingService.processReceipt(any())).thenReturn(mockResponse);

        MockMultipartFile image = new MockMultipartFile(
                "image", "ticket.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/v1/receipts/process")
                        .file(image)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedSupplier").value("PROVEEDOR TEST"))
                .andExpect(jsonPath("$.detectedDate").value("15/05/2026"))
                .andExpect(jsonPath("$.imageUrl").value(
                        "https://test.supabase.co/storage/v1/object/public/receipts/img.jpg"))
                .andExpect(jsonPath("$.lines[0].name").value("Cloro 5L"))
                .andExpect(jsonPath("$.lines[0].confidence").value(0.95));
    }

    /**
     * RED: Verifica que se retorna 403 cuando el usuario no es ADMIN.
     */
    @Test
    void shouldReturn403WhenNotAdminForProcess() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "ticket.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/v1/receipts/process")
                        .file(image)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    /**
     * RED: Verifica que se retorna 422 cuando el OCR falla.
     */
    @Test
    void shouldReturn422WhenOcrFails() throws Exception {
        when(processingService.processReceipt(any()))
                .thenThrow(new OcrProcessingException("No text detected in image"));

        MockMultipartFile image = new MockMultipartFile(
                "image", "blurry.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/v1/receipts/process")
                        .file(image)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OCR_PROCESSING_ERROR"))
                .andExpect(jsonPath("$.message").value("No text detected in image"));
    }

    /**
     * RED: Verifica que se retorna 401 cuando no hay token JWT.
     */
    @Test
    void shouldReturn401WhenNoTokenForProcess() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "ticket.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/v1/receipts/process")
                        .file(image))
                .andExpect(status().isUnauthorized());
    }

    // ===================== Tests de confirmación (confirm) =====================

    /**
     * RED: Verifica que POST /api/v1/receipts/confirm retorna 201
     * con PurchaseResponse cuando la confirmación es exitosa.
     */
    @Test
    void shouldReturn201WithPurchaseResponse() throws Exception {
        PurchaseResponse mockResponse = new PurchaseResponse(
                1L,
                "https://storage.example.com/receipts/img.jpg",
                "Proveedor Test",
                LocalDate.of(2026, 5, 15),
                new BigDecimal("500.00"),
                List.of(new PurchaseItemResponse(1L, "Cloro 5L", 2,
                        new BigDecimal("150.00"), new BigDecimal("300.00"), 10L))
        );

        when(confirmationService.confirm(any(), any())).thenReturn(mockResponse);

        String requestBody = """
                {
                    "imageUrl": "https://storage.example.com/receipts/img.jpg",
                    "supplierName": "Proveedor Test",
                    "purchaseDate": "2026-05-15",
                    "lines": [
                        {
                            "description": "Cloro 5L",
                            "quantity": 2,
                            "unitPrice": 150.00,
                            "bulkProductId": 10
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.supplierName").value("Proveedor Test"))
                .andExpect(jsonPath("$.total").value(500.00))
                .andExpect(jsonPath("$.items[0].description").value("Cloro 5L"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    /**
     * RED: Verifica que se retorna 403 cuando operador intenta confirmar.
     */
    @Test
    void shouldReturn403WhenNotAdminForConfirm() throws Exception {
        String requestBody = """
                {
                    "imageUrl": "https://example.com/img.jpg",
                    "supplierName": "Proveedor",
                    "purchaseDate": "2026-05-15",
                    "lines": [
                        {
                            "description": "Producto X",
                            "quantity": 1,
                            "unitPrice": 100.00,
                            "bulkProductId": null
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    /**
     * RED: Verifica que se retorna 401 sin token JWT.
     */
    @Test
    void shouldReturn401WhenNoTokenForConfirm() throws Exception {
        String requestBody = """
                {
                    "imageUrl": "https://example.com/img.jpg",
                    "supplierName": "Proveedor",
                    "purchaseDate": "2026-05-15",
                    "lines": [
                        {
                            "description": "Producto X",
                            "quantity": 1,
                            "unitPrice": 100.00,
                            "bulkProductId": null
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    /**
     * RED: Verifica que se retorna 400 cuando la lista de items está vacía.
     */
    @Test
    void shouldReturn400WhenEmptyProductLines() throws Exception {
        String requestBody = """
                {
                    "imageUrl": "https://example.com/img.jpg",
                    "supplierName": "Proveedor",
                    "purchaseDate": "2026-05-15",
                    "lines": []
                }
                """;

        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    // ===================== Fix 3 — RED: Validación MIME en controller =====================

    /**
     * RED: Verifica que el controller rechaza archivos PDF (formato no soportado).
     * SPEC: REC-002 "Unsupported format" — 400 Bad Request para formatos no imagen.
     * 
     * WHY: La validación de MIME type ocurre en el controller ANTES de llamar
     *      al processingService. El mock nunca se invoca para formatos inválidos.
     */
    @Test
    void shouldReturn400WhenUnsupportedFormat() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "image", "document.pdf", MediaType.APPLICATION_PDF_VALUE,
                new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/v1/receipts/process")
                        .file(pdfFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Unsupported file type")));
    }

    /**
     * RED: Verifica que el controller rechaza archivos vacíos a nivel HTTP.
     * SPEC: REC-002 "Empty file" — 400 Bad Request para multipart vacío.
     */
    @Test
    void shouldReturn400WhenEmptyFileAtHttpLevel() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/receipts/process")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    /**
     * RED: Verifica que se retorna 400 cuando quantity es 0.
     */
    @Test
    void shouldReturn400WhenZeroQuantity() throws Exception {
        String requestBody = """
                {
                    "imageUrl": "https://example.com/img.jpg",
                    "supplierName": "Proveedor",
                    "purchaseDate": "2026-05-15",
                    "lines": [
                        {
                            "description": "Producto X",
                            "quantity": 0,
                            "unitPrice": 100.00,
                            "bulkProductId": null
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/receipts/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }
}
