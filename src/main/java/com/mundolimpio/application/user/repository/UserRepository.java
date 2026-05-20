package com.mundolimpio.application.user.repository;

import com.mundolimpio.application.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para la entidad User.
 * <p>
 * WHAT: Agrega findByEmail, existsByEmail y existsByUsername como queries
 * derivadas de Spring Data JPA.
 * <p>
 * WHY: findByEmail() se usa en login y refresh (CustomUserDetailsService y
 * JwtAuthenticationFilter). existsByEmail() verifica duplicados en registro.
 * existsByUsername() resuelve colisiones en generación de username.
 * <p>
 * DIFFERENCES: Antes solo tenía findByUsername(). Ahora findByUsername()
 * coexiste para UserManagementService (admin CRUD que busca por username real).
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
