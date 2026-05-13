package com.mundolimpio.application.user.dto;

import java.time.Instant;

/**
 * DTO de respuesta para consultas de usuario.
 *
 * QUÉ HACE: Expone los datos públicos de un usuario: su ID, username,
 * rol (como String) y fecha de creación. NO expone la contraseña
 * (es información sensible).
 *
 * POR QUÉ: Usamos un record de Java para garantizar inmutabilidad
 * y reducir boilerplate (constructor, getters, equals, hashCode, toString
 * se generan automáticamente). Sigue el patrón de InventoryResponse.
 *
 * DIFERENCIA con InventoryResponse:
 *   - InventoryResponse expone datos de stock (productId, currentStock).
 *   - UserResponse expone datos de usuario (username, role, createdAt).
 *   - Ambos son records inmutables que no exponen datos sensibles.
 *
 * @param id        Identificador único del usuario
 * @param username  Nombre de usuario (login)
 * @param role      Rol del usuario como String (ADMIN u OPERATOR)
 * @param createdAt Momento en que se creó el usuario
 */
public record UserResponse(
        Long id,
        String username,
        String role,
        Instant createdAt
) {}
