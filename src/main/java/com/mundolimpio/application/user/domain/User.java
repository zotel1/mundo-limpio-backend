package com.mundolimpio.application.user.domain;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Entidad User que representa un usuario del sistema.
 * Implementa UserDetails para integrarse con Spring Security.
 * <p>
 * WHAT: getUsername() ahora devuelve email para que Spring Security y JwtService
 * usen email como identificador de autenticación sin modificar esos componentes.
 * El username real se preserva vía getRawUsername() como display name.
 * <p>
 * WHY: El frontend Flutter envía email+password. El backend debe aceptar email
 * como identificador primario. JWT sub = email automáticamente.
 * <p>
 * DIFFERENCES: Antes getUsername() devolvía el username. Ahora devuelve email.
 * Constructor anterior (3 params) delegado al nuevo (4 params) con email fallback.
 * */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public User() {
    }

    /**
     * Constructor anterior (3 params) — mantenido para backward compatibility
     * durante la migración. Delega al nuevo constructor con email fallback.
     *
     * @deprecated Usar {@link #User(String, String, String, Role)} con email explícito.
     *             Será removido en Phase 2 cuando todos los tests migren al nuevo constructor.
     */
    @Deprecated
    public User(String username, String password, Role role) {
        this(username, username + "@mundolimpio.com", password, role);
    }

    /**
     * Nuevo constructor (4 params) con email explícito.
     *
     * @param username username auto-generado desde el prefijo del email (display name)
     * @param email    email del usuario (identificador de autenticación)
     * @param password contraseña (debe estar encodeada)
     * @param role     rol del usuario (ADMIN u OPERATOR)
     */
    public User(String username, String email, String password, Role role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * WHAT: Devuelve el email como identificador de Spring Security.
     * WHY: Spring Security + JwtService llaman a getUsername() para el JWT "sub".
     * Devolviendo email, el JWT sub = email sin modificar JwtService ni filtros.
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Devuelve el username real (display name auto-generado).
     * WHAT: Acceso separado al campo username para respuestas y admin UI.
     * WHY: getUsername() ahora devuelve email; necesitamos exponer el username
     * real sin romper el contrato de UserDetails.
     */
    public String getRawUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
