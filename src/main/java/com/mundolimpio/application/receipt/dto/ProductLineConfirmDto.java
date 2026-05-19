package com.mundolimpio.application.receipt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * DTO de entrada para confirmar una línea de producto (parte de ReceiptConfirmRequest).
 * 
 * POR QUÉ validaciones Jakarta:
 * - @NotBlank en description: no tiene sentido un item sin descripción.
 * - @Positive en quantity y unitPrice: deben ser mayores a 0.
 * - Estas validaciones se ejecutan ANTES de llegar al servicio (primera línea de defensa).
 * 
 * DIFFERENCES con ProductLineDto:
 * - Acá description es el nombre revisado/corregido por el admin (no el raw del OCR).
 * - No tiene confidence porque ya fue revisado por un humano.
 * - bulkProductId puede ser asignado manualmente por el admin.
 */
public record ProductLineConfirmDto(
        @NotBlank(message = "Description cannot be blank")
        String description,

        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @Positive(message = "Unit price must be greater than zero")
        BigDecimal unitPrice,

        Long bulkProductId  // nullable: el admin puede elegir no matchear
) {}
