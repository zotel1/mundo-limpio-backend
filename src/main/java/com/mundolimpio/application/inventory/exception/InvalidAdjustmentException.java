package com.mundolimpio.application.inventory.exception;

/**
 * Excepción lanzada cuando un ajuste de inventario no es válido.
 *
 * QUE HACE: Se lanza cuando se intenta realizar un ajuste que dejaría
 * el stock en negativo o que viola alguna regla de negocio del dominio
 * de inventario.
 *
 * POR QUE: El stock no puede ser negativo (no tiene sentido físico).
 * Cuando un ajuste intenta decrementar más stock del disponible, esta
 * excepción se lanza en la capa de servicio y se traduce a HTTP 400
 * en GlobalExceptionHandler con código "INSUFFICIENT_STOCK".
 *
 * DIFERENCIA con otras excepciones del proyecto:
 *   - ProductionBatchNotFoundException se lanza cuando un batch no existe.
 *   - InvalidAdjustmentException se lanza cuando el ajuste ES inválido
 *     por regla de negocio, no porque falte una entidad.
 *   - Ambas extienden RuntimeException y serán manejadas por
 *     GlobalExceptionHandler en la fase PR #2.
 */
public class InvalidAdjustmentException extends RuntimeException {

    public InvalidAdjustmentException(String message) {
        super(message);
    }
}
