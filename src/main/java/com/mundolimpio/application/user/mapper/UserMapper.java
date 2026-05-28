package com.mundolimpio.application.user.mapper;

import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.UserResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper para convertir entre entidades User y DTOs.
 * <p>
 * WHAT: Mapea user.getRoles() a List<String> para el nuevo campo `roles`
 * y user.getRole().name() al campo `role` deprecated (primer rol o null).
 * WHY: El modelo multi-rol requiere exponer todos los roles del usuario.
 * El campo `role` deprecated se mantiene para backward compatibility con
 * clientes que aun no migraron al nuevo campo `roles`.
 * DIFFERENCES: Antes solo mapeaba user.getRole().name(). Ahora genera ambos:
 * la lista completa de roles y el primer rol como String legacy.
 */
@Component
public class UserMapper {

    /**
     * Convierte una entidad User a un DTO UserResponse.
     * <p>
     * WHAT: Extrae todos los roles del usuario como List<String> y el primer
     * rol como String legacy (o null si no tiene roles).
     * WHY: getRoles() devuelve el Set<Role> completo; lo convertimos a List<String>
     * para serializacion JSON. El campo role deprecated usa getRole() que
     * devuelve el primer rol del Set (o null si esta vacio).
     *
     * @param user La entidad User desde la base de datos
     * @return UserResponse listo para enviar al cliente (sin contraseña)
     */
    public UserResponse toResponse(User user) {
        List<String> roleStrings = user.getRoles().stream()
                .map(Enum::name)
                .toList();
        String legacyRole = user.getRole() != null ? user.getRole().name() : null;

        return new UserResponse(
                user.getId(),
                user.getRawUsername(),
                user.getEmail(),
                legacyRole,
                user.getCreatedAt(),
                roleStrings
        );
    }
}
