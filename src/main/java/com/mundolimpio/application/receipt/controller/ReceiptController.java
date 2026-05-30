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

import java.util.List;
import java.util.NoSuchElementException;

/**
 * WHAT: Controlador REST para el módulo de tickets de compra (receipts).
 * WHY: Expone los endpoints del flujo de procesamiento OCR de tickets.
 *      Accesible por ADMIN, STOCK_MANAGER y STOCK_OPERATOR.
 * 
 * ENDPOINTS:
 * POST /api/v1/receipts/process — Sube imagen del ticket y la procesa con OCR
 * POST /api/v1/receipts/confirm  — Confirma los datos revisados y persiste la compra
 * GET  /api/v1/receipts         — Lista todas las compras
 * GET  /api/v1/receipts/{id}    — Obtiene detalle de una compra
 *
 * DIFFERENCES con BulkProductController:
 * - Este controlador maneja multipart/form-data (imagen) en vez de JSON.
 * - Usa @PreAuthorize a nivel de método (mismo patrón que BulkProductController).
 * 
 * DIFFERENCES con PR 1 (HIGH-2):
 * - Nuevos endpoints GET para listar y ver detalle de compras.
 * - Roles para consulta: ADMIN, STOCK_MANAGER, ACCOUNTANT.
 */
@RestController
@RequestMapping("/api/v1/receipts")
@Tag(name = "Receipts", description = "Endpoints para procesamiento OCR de tickets de compra (ADMIN, STOCK_MANAGER, STOCK_OPERATOR)")
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
     * 1. Valida que el archivo sea JPEG/PNG (MIME type)
     * 2. Delega en ReceiptProcessingService (upload + OCR + parseo)
     * 3. Retorna ReceiptProcessResponse con líneas de producto detectadas
     *
     * @param image Archivo multipart con la foto del ticket (JPEG o PNG)
     * @return 200 OK con ReceiptProcessResponse (líneas de producto, proveedor, fecha, URL imagen)
     * @throws IllegalArgumentException → 400 Bad Request si el formato no es soportado
     * @throws OcrProcessingException → 422 Unprocessable Entity (manejado por ReceiptExceptionHandler)
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'STOCK_OPERATOR')")
    // WHAT: Procesamiento OCR de tickets ampliado a roles de almacen
    // WHY: STOCK_MANAGER y STOCK_OPERATOR reciben mercancia y escanean tickets
    @Operation(
            summary = "Process receipt image with OCR",
            description = "Uploads a receipt photo, stores it in Supabase, and extracts product data via Tesseract OCR. ADMIN, STOCK_MANAGER, and STOCK_OPERATOR can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OCR processing successful — product data extracted"),
            @ApiResponse(responseCode = "400", description = "Invalid file type or missing image"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access"),
            @ApiResponse(responseCode = "422", description = "OCR processing failed — image unreadable")
    })
    public ResponseEntity<ReceiptProcessResponse> processReceipt(
            @RequestParam("image") MultipartFile image) {

        // WHAT: Validación de archivo vacío en el controller.
        // WHY: SPEC REC-002 — archivos vacíos deben rechazarse con 400.
        if (image.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // WHAT: Validación temprana de MIME type en el controller.
        // WHY: Falla rápido antes de delegar al servicio, evita subir archivos
        //      inválidos a Supabase Storage y mejora la testabilidad HTTP.
        //      SPEC: REC-002 — solo image/jpeg y image/png están permitidos.
        String contentType = image.getContentType();
        if (contentType == null ||
                (!contentType.equals(MediaType.IMAGE_JPEG_VALUE) &&
                 !contentType.equals(MediaType.IMAGE_PNG_VALUE))) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType +
                    ". Only JPEG and PNG are accepted.");
        }

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
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER')")
    // WHAT: Confirmacion de compra ampliada a STOCK_MANAGER
    // WHY: STOCK_MANAGER revisa y confirma las compras recibidas
    @Operation(
            summary = "Confirm receipt and persist purchase",
            description = "Receives reviewed OCR data, persists Purchase with items, creates Supplier if needed, and updates stock. ADMIN and STOCK_MANAGER can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purchase confirmed and persisted successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error — empty lines, zero quantity, invalid fields"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Only ADMIN can access")
    })
    public ResponseEntity<PurchaseResponse> confirmReceipt(
            @Valid @RequestBody ReceiptConfirmRequest request) {

        // Obtener el User autenticado del SecurityContext
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (!(principal instanceof User admin)) {
            throw new IllegalStateException("Authenticated principal is not a User");
        }

        PurchaseResponse response = confirmationService.confirm(request, admin);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ========================= GET ALL =========================

    /**
     * Lista todas las compras (purchases).
     * 
     * WHAT: Retorna lista de compras sin paginación (MVP).
     * WHY: HIGH-2 — ADMIN, STOCK_MANAGER y ACCOUNTANT necesitan consultar compras.
     * 
     * @return Lista de PurchaseResponse
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'ACCOUNTANT')")
    // WHAT: ACCOUNTANT puede consultar compras
    // WHY: HIGH-2 — el contador necesita ver compras para contabilidad
    @Operation(
            summary = "List all purchases",
            description = "Returns all purchases. ADMIN, STOCK_MANAGER, and ACCOUNTANT can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of purchases retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: insufficient role")
    })
    public ResponseEntity<List<PurchaseResponse>> getAllPurchases() {
        List<PurchaseResponse> purchases = confirmationService.findAll();
        return ResponseEntity.ok(purchases);
    }

    // ========================= GET BY ID =========================

    /**
     * Obtiene una compra por su ID.
     * 
     * WHAT: Retorna el detalle de una compra específica con sus items.
     * WHY: HIGH-2 — necesario para ver detalle de una compra individual.
     * 
     * @param id ID de la compra
     * @return PurchaseResponse con items
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK_MANAGER', 'ACCOUNTANT')")
    @Operation(
            summary = "Get purchase by ID",
            description = "Returns a purchase by its ID with all items. ADMIN, STOCK_MANAGER, and ACCOUNTANT can access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: no authentication token"),
            @ApiResponse(responseCode = "403", description = "Forbidden: insufficient role"),
            @ApiResponse(responseCode = "404", description = "Purchase not found")
    })
    public ResponseEntity<PurchaseResponse> getPurchaseById(@PathVariable Long id) {
        try {
            PurchaseResponse response = confirmationService.findById(id);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
