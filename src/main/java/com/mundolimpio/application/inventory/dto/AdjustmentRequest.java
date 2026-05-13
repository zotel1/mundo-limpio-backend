package com.mundolimpio.application.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO de solicitud para realizar un ajuste manual de inventario.
 *
 * QUE HACE: Recibe los datos necesarios para ajustar el stock de un
 * producto: el tipo de ajuste (ADJUSTMENT, BREAKAGE, RETURN, QUALITY_LOSS),
 * la cantidad con signo (positivo = aumento, negativo = disminución) y
 * la razón del ajuste.
 *
 * POR QUE: Usamos un record para mantener inmutabilidad y reducir
 * boilerplate (misma razón que InventoryResponse). Las validaciones con
 * Jakarta Validation (@NotNull, @NotBlank) garantizan que los datos
 * lleguen completos antes de llegar al servicio.
 *
 * DIFERENCIA con ProductRequest:
 *   - ProductRequest usa @Pattern en el SKU para validar formato.
 *   - AdjustmentRequest no necesita @PositiveOrZero en quantity porque
 *     el diseño usa convención de signo: valores negativos son válidos
 *     y representan decrementos de stock. La validación de que el stock
 *     no quede negativo se hace en la capa de servicio, no en el DTO.
 *
 * @param type     Tipo de ajuste (ADJUSTMENT, BREAKAGE, RETURN, QUALITY_LOSS)
 * @param quantity Cantidad con signo (positivo = aumenta stock, negativo = disminuye)
 * @param reason   Descripción del motivo del ajuste
 */
public record AdjustmentRequest(
        @NotBlank(message = "Adjustment type is required")
        String type,

        @NotNull(message = "Quantity is required")
        BigDecimal quantity,

        @NotBlank(message = "Reason is required")
        String reason
) {}
