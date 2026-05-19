package com.mundolimpio.application.receipt.controller;

import com.mundolimpio.application.receipt.dto.ProductLineDto;
import com.mundolimpio.application.receipt.dto.ReceiptProcessResponse;
import com.mundolimpio.application.receipt.exception.OcrProcessingException;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WHAT: Tests de integración para ReceiptController (POST /api/v1/receipts/process).
 * WHY: Verifica el flujo HTTP completo: autenticación JWT, validación de roles,
 *      manejo de errores de OCR, y respuesta exitosa con datos estructurados.
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

    // ===================== Tests de procesamiento exitoso =====================

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
    void shouldReturn403WhenNotAdmin() throws Exception {
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
    void shouldReturn401WhenNoToken() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "ticket.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{0x01, 0x02, 0x03});

        mockMvc.perform(multipart("/api/v1/receipts/process")
                        .file(image))
                .andExpect(status().isUnauthorized());
    }
}
