package com.mundolimpio.application.user.exception;

/**
 * Excepción lanzada cuando no se encuentra un usuario por su ID.
 *
 * QUÉ HACE: Se lanza cuando se consulta, modifica el rol o resetea
 * la contraseña de un userId que no existe en la tabla users.
 *
 * POR QUÉ: En lugar de devolver un Optional.empty() genérico, esta
 * excepción da un mensaje claro con el ID del usuario y se traduce
 * a HTTP 404 en UserExceptionHandler (o GlobalExceptionHandler
 * como fallback).
 *
 * DIFERENCIA con InventoryNotFoundException:
 *   - InventoryNotFoundException busca por productId (Long) y usa
 *     mensaje "Inventory not found for product ID: ...".
 *   - UserNotFoundException busca por userId (Long) y usa mensaje
 *     "User not found with ID: ...".
 *   - Ambas extienden RuntimeException y son manejadas por handlers
 *     específicos con @Order(HIGHEST_PRECEDENCE).
 */
public class UserNotFoundException extends RuntimeException {

    /**
     * @param id El ID del usuario que no se encontró
     */
    public UserNotFoundException(Long id) {
        super("User not found with ID: " + id);
    }
}
