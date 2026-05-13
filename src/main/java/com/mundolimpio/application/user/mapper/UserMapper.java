package com.mundolimpio.application.user.mapper;

import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper para convertir entre entidades User y DTOs.
 *
 * QUÉ HACE: Centraliza la conversión de User (entidad JPA) a
 * UserResponse (DTO de respuesta). Convierte el enum Role a String
 * para la capa de presentación.
 *
 * POR QUÉ: Separar la lógica de conversión del servicio mantiene cada
 * clase con una única responsabilidad. El servicio se enfoca en reglas
 * de negocio, el mapper se enfoca en transformación de datos.
 *
 * DIFERENCIA con InventoryMapper:
 *   - InventoryMapper usa inventory.getProduct().getId() para obtener
 *     el productId desde la relación @ManyToOne.
 *   - UserMapper usa directamente user.getId(), user.getUsername(),
 *     user.getRole().name() (convierte enum a String).
 *   - Ambos son @Component (no MapStruct) para mantener consistencia
 *     con el patrón existente del proyecto.
 *   - Ambos tienen un único método toResponse() por ahora.
 */
@Component
public class UserMapper {

    /**
     * Convierte una entidad User a un DTO UserResponse.
     * Convierte el enum Role a su representación String.
     *
     * @param user La entidad User desde la base de datos
     * @return UserResponse listo para enviar al cliente (sin contraseña)
     */
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}
