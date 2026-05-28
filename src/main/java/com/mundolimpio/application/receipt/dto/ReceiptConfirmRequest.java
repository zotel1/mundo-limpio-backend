package com.mundolimpio.application.receipt.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO de entrada para el endpoint POST /api/v1/receipts/confirm.
 * El admin envía los datos revisados/corregidos del OCR para persistir la compra.
 * 
 * POR QUÉ @Valid en lines:
 * - Las validaciones de ProductLineConfirmDto (@NotBlank, @Positive) se ejecutan
 *   en cascada. Sin @Valid, Spring ignora las validaciones internas de la lista.
 * 
 * POR QUÉ @NotEmpty en lines:
 * - No tiene sentido confirmar una compra sin items.
 */
public record ReceiptConfirmRequest(
        @NotBlank(message = "Image URL cannot be blank")
        String imageUrl,

        @NotBlank(message = "Supplier name cannot be blank")
        String supplierName,

        @NotNull(message = "Purchase date cannot be null")
        LocalDate purchaseDate,

        @NotEmpty(message = "At least one product line is required")
        @Valid
        List<ProductLineConfirmDto> lines
) {}
