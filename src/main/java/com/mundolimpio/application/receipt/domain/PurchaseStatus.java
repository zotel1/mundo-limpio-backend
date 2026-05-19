package com.mundolimpio.application.receipt.domain;

/**
 * WHAT: Enum que define los estados posibles de una compra (Purchase).
 * WHY: Separamos PENDING (recién procesada por OCR, pendiente de revisión admin)
 *      de CONFIRMED (admin revisó y confirmó la compra, stock actualizado).
 */
public enum PurchaseStatus {
    /**
     * Compra recién creada por OCR, pendiente de revisión del admin.
     * En este estado, NO se actualiza el stock de BulkProduct.
     */
    PENDING,

    /**
     * Compra confirmada por el admin después de revisar/corregir los datos del OCR.
     * En este estado, SÍ se actualiza el stock de BulkProduct.
     */
    CONFIRMED
}
