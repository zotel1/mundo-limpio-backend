package com.mundolimpio.application.user.domain;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Entidad User que representa un usuario del sistema.
 * Implementa UserDetails para integrarse con Spring Security.
 * <p>
 * WHAT: Soporta multiples roles via @ElementCollection Set<Role> mapeado a
 * la tabla user_roles. getUsername() devuelve email para que Spring Security
 * y JwtService usen email como identificador de autenticación.
 * <p>
 * WHY: El modelo RBAC se expande de 2 roles (ADMIN/OPERATOR) a 6 roles
 * con asignacion multiple. Un usuario puede ser STOCK_MANAGER y SALES_CLERK
 * simultaneamente (UR-R2). El constructor varargs Role... mantiene backward
 * compatibility con codigo existente.
 * <p>
 * DIFFERENCES: Antes User tenia un solo Role (campo role). Ahora tiene
 * Set<Role> (campo roles) con @ElementCollection. getRole() y setRole()
 * quedan como @Deprecated para compatibilidad durante la transicion.
 */
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

    /**
     * WHAT: Set de roles asignados al usuario.
     * WHY: @ElementCollection con @CollectionTable mapea la relacion 1:N
     * a la tabla user_roles sin necesidad de una entidad intermedia.
     * EAGER porque siempre necesitamos los roles para getAuthorities().
     */
    @ElementCollection(fetch = FetchType.EAGER, targetClass = Role.class)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public User() {
    }

    /**
     * Constructor anterior (3 params) — mantenido para backward compatibility
     * durante la migración. Delega al nuevo constructor con email fallback.
     *
     * @deprecated Usar {@link #User(String, String, String, Role...)} con email explícito.
     *             Será removido cuando todos los tests migren al nuevo constructor.
     */
    @Deprecated
    public User(String username, String password, Role role) {
        this(username, username + "@mundolimpio.com", password, role);
    }

    /**
     * Constructor con varargs Role... para asignacion multiple de roles.
     * <p>
     * WHAT: Acepta cero o mas roles. Si se pasa un solo rol (ej: Role.ADMIN),
     * funciona igual que el constructor anterior — backward compatible.
     * WHY: new User("u","e","p", Role.ADMIN) sigue compilando sin cambios.
     *
     * @param username username auto-generado desde el prefijo del email (display name)
     * @param email    email del usuario (identificador de autenticación)
     * @param password contraseña (debe estar encodeada)
     * @param roles    cero o mas roles asignados al usuario
     */
    public User(String username, String email, String password, Role... roles) {
        this.username = username;
        this.email = email;
        this.password = password;
        if (roles.length > 0) {
            this.roles = new HashSet<>(Set.of(roles));
        }
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

    /**
     * WHAT: Devuelve todos los roles asignados al usuario.
     * WHY: Reemplaza al antiguo getRole() para soportar multiples roles.
     *
     * @return Set<Role> inmutable copia del set interno (nunca null)
     */
    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    /**
     * WHAT: Reemplaza completamente los roles del usuario.
     * WHY: Usado por UserManagementService.changeRoles() para asignar
     * el nuevo conjunto de roles desde el PATCH /users/{id}/roles.
     *
     * @param roles nuevo conjunto de roles (no null)
     */
    public void setRoles(Set<Role> roles) {
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
    }

    /**
     * WHAT: Devuelve el primer rol del Set (o null si no tiene).
     *
     * @return el primer Role o null
     * @deprecated Usar {@link #getRoles()} para obtener todos los roles.
     *             Este metodo existe solo para backward compatibility
     *             durante la transicion al modelo multi-rol.
     */
    @Deprecated
    public Role getRole() {
        return roles.isEmpty() ? null : roles.iterator().next();
    }

    /**
     * WHAT: Reemplaza todos los roles con un solo rol.
     * WHY: Backward compatibility con codigo que usaba setRole() para
     * asignar un unico rol. El comportamiento original era REEMPLAZAR,
     * no agregar. Mantenemos esa semantica.
     *
     * @param role el unico rol a asignar (null = sin roles)
     * @deprecated Usar {@link #setRoles(Set)} para asignar multiples roles.
     *             Este metodo existe solo para backward compatibility
     *             durante la transicion al modelo multi-rol.
     */
    @Deprecated
    public void setRole(Role role) {
        this.roles.clear();
        if (role != null) {
            this.roles.add(role);
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * WHAT: Devuelve todas las autoridades de Spring Security para este usuario.
     * Cada Role se mapea a SimpleGrantedAuthority con prefijo "ROLE_".
     * WHY: UR-R2 — Spring Security evalua hasAnyRole() sobre la union de todas
     * las autoridades. Si un usuario tiene STOCK_MANAGER y SALES_CLERK, tendra
     * ROLE_STOCK_MANAGER y ROLE_SALES_CLERK.
     *
     * @return coleccion de GrantedAuthority (vacia si no tiene roles)
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
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
