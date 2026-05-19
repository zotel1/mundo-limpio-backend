package com.mundolimpio.application.receipt.controller;

import com.mundolimpio.application.receipt.dto.PurchaseResponse;
import com.mundolimpio.application.receipt.dto.ReceiptConfirmRequest;
import com.mundolimpio.application.receipt.dto.ReceiptProcessResponse;
import com.mundolimpio.application.receipt.exception.OcrProcessingException;
import com.mundolimpio.application.receipt.service.ReceiptConfirmationService;
import com.mundolimpio.application.receipt.service.ReceiptProcessingService;
import com.mundolimpio.application.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * WHAT: Controlador REST para el módulo de tickets de compra (receipts).
 * WHY: Expone los endpoints del flujo de procesamiento OCR de tickets.
 *      Solo accesible por usuarios con rol ADMIN.
 * 
 * ENDPOINTS:
 * POST /api/v1/receipts/process — Sube imagen del ticket y la procesa con OCR
 * POST /api/v1/receipts/confirm  — Confirma los datos revisados y persiste la compra
 *
 * DIFFERENCES con BulkProductController:
 * - Este controlador maneja multipart/form-data (imagen) en vez de JSON.
 * - Usa @PreAuthorize a nivel de método (mismo patrón que BulkProductController).
 */
@RestController
@RequestMapping("/api/v1/receipts")
@Tag(name = "Receipts", description = "Endpoints para procesamiento OCR de tickets de compra (ADMIN only)")
public class ReceiptController {

    private final ReceiptProcessingService processingService;
    private final ReceiptConfirmationService confirmationService;

    /**
     * Inyección por constructor de ambos servicios.
     */
    public ReceiptController(ReceiptProcessingService processingService,
                              ReceiptConfirmationService confirmationService) {
        this.processingService = processingService;
        this.confirmationService = confirmationService;
    }

    // ==================== PROCESS ENDPOINT ====================

    /**
     * Procesa una imagen de ticket de compra via OCR.
     * 
     * WHAT: Recibe una foto del ticket (JPEG/PNG), la sube a Supabase Storage,
     *       ejecuta OCR con Tesseract (español), y retorna los datos extraídos
     *       para que el admin los revise antes de confirmar.
     * 
     * FLUJO:
     * 1. Valida que el archivo sea JPEG/PNG
     * 2. Delega en ReceiptProcessingService (upload + OCR + parseo)
     * 3. Retorna ReceiptProcessResponse con líneas de producto detectadas
     *
     * @param image Archivo multipart con la foto del ticket (JPEG o PNG)
     * @return 200 OK con ReceiptProcessResponse (líneas de producto, proveedor, fecha, URL imagen)
     * @throws OcrProcessingException → 422 Unprocessable Entity (manejado por ReceiptExceptionHandler)
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Process receipt image with OCR",
            description = "Uploads a receipt photo, stores it in Supabase, and extracts product data via Tesseract OCR. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OCR processing successful — product data extracted"),
            @ApiResponse(responseCode = "400", description = "Invalid file type or missing image"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access"),
            @ApiResponse(responseCode = "422", description = "OCR processing failed — image unreadable")
    })
    public ResponseEntity<ReceiptProcessResponse> processReceipt(
            @RequestParam("image") MultipartFile image) {

        ReceiptProcessResponse response = processingService.processReceipt(image);
        return ResponseEntity.ok(response);
    }

    // ==================== CONFIRM ENDPOINT ====================

    /**
     * Confirma los datos revisados de una compra y la persiste.
     * 
     * WHAT: Recibe los datos del ticket revisados/corregidos por el admin,
     *       persiste la Purchase + PurchaseItems, crea el Supplier si no existe,
     *       actualiza el stock de BulkProduct para items matcheados.
     * 
     * FLUJO:
     * 1. Valida el request body (@Valid + Jakarta Bean Validation)
     * 2. Obtiene el User autenticado del SecurityContext
     * 3. Delega en ReceiptConfirmationService (find-or-create Supplier → persist → stock update)
     * 4. Retorna 201 CREATED con PurchaseResponse
     *
     * @param request DTO con los datos revisados por el admin
     * @return 201 CREATED con PurchaseResponse (compra persistida)
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Confirm receipt and persist purchase",
            description = "Receives reviewed OCR data, persists Purchase with items, creates Supplier if needed, and updates stock. Only ADMIN can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purchase confirmed and persisted successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error — empty lines, zero quantity, invalid fields"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<PurchaseResponse> confirmReceipt(
            @Valid @RequestBody ReceiptConfirmRequest request) {

        // Obtener el User autenticado del SecurityContext
        User admin = (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        PurchaseResponse response = confirmationService.confirm(request, admin);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
