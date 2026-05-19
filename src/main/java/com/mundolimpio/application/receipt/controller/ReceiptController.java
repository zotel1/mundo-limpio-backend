package com.mundolimpio.application.receipt.controller;

import com.mundolimpio.application.receipt.dto.ReceiptProcessResponse;
import com.mundolimpio.application.receipt.exception.OcrProcessingException;
import com.mundolimpio.application.receipt.service.ReceiptProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * WHAT: Controlador REST para el módulo de tickets de compra (receipts).
 * WHY: Expone los endpoints del flujo de procesamiento OCR de tickets.
 *      Solo accesible por usuarios con rol ADMIN.
 * 
 * ENDPOINTS:
 * POST /api/v1/receipts/process — Sube imagen del ticket y la procesa con OCR
 * POST /api/v1/receipts/confirm  — Confirma los datos revisados (PR 3)
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

    /**
     * Inyección por constructor.
     */
    public ReceiptController(ReceiptProcessingService processingService) {
        this.processingService = processingService;
    }

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
}
