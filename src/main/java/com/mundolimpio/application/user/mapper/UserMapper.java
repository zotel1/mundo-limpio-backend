package com.mundolimpio.application.user.mapper;

import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper para convertir entre entidades User y DTOs.
 * <p>
 * WHAT: Ahora mapea user.getEmail() y user.getRawUsername() al UserResponse.
 * WHY: getUsername() devuelve email (contrato de Spring Security); necesitamos
 * getRawUsername() para el display name y getEmail() para el campo email explícito.
 * DIFFERENCES: Antes usaba user.getUsername() tanto para email como para username
 * (porque getUsername() antes devolvía el username). Ahora separa ambos accesos.
 */
@Component
public class UserMapper {

    /**
     * Convierte una entidad User a un DTO UserResponse.
     * Usa getRawUsername() para el display name y getEmail() para el email.
     *
     * @param user La entidad User desde la base de datos
     * @return UserResponse listo para enviar al cliente (sin contraseña)
     */
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getRawUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}
