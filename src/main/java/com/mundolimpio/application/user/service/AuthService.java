package com.mundolimpio.application.user.service;

import com.mundolimpio.application.security.service.CustomUserDetailsService;
import com.mundolimpio.application.security.service.JwtService;
import com.mundolimpio.application.user.domain.Role;
import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.dto.LoginRequest;
import com.mundolimpio.application.user.dto.LoginResponse;
import com.mundolimpio.application.user.dto.RefreshRequest;
import com.mundolimpio.application.user.dto.RegisterRequest;
import com.mundolimpio.application.user.exception.InvalidRefreshTokenException;
import com.mundolimpio.application.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Servicio de autenticación: registro, login y refresh de tokens.
 * <p>
 * WHAT: register() acepta email+password, verifica unicidad de email,
 * auto-genera username desde el prefijo del email, crea usuario SIN roles
 * (UR-R4: admin asigna roles luego), y devuelve LoginResponse con campos
 * email + username + roles. login() y refresh() autentican por email.
 * <p>
 * WHY: El frontend Flutter envía email+password. Spring Security autentica
 * con UsernamePasswordAuthenticationToken(email, password). User.getUsername()
 * devuelve email → JWT sub = email automáticamente. El usuario nuevo no tiene
 * roles hasta que un ADMIN los asigne via PATCH /users/{id}/roles.
 * <p>
 * DIFFERENCES: Antes register asignaba Role.SALES_CLERK por defecto. Ahora
 * asigna Set.of() (sin roles). LoginResponse ahora incluye campo roles
 * con la lista completa de roles del usuario.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;

    @Value("${application.security.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    /** Alfabeto para generación de sufijos aleatorios en username. */
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager,
                       CustomUserDetailsService customUserDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
    }

    /**
     * Registra un nuevo usuario con email+password.
     * <p>
     * WHAT: Verifica que el email no esté duplicado, genera un username único
     * desde el prefijo del email, persiste el usuario SIN roles
     * (UR-R4: admin asigna roles luego via PATCH /users/{id}/roles),
     * y devuelve LoginResponse con email + username + roles + tokens.
     * WHY: El frontend envía email; el username es interno para display/admin.
     * El usuario nuevo no debe tener permisos hasta que un ADMIN se los asigne.
     * DIFFERENCES: Antes asignaba Role.SALES_CLERK. Ahora asigna Set.of().
     *
     * @param request DTO con email y password
     * @return LoginResponse con tokens, email, username, roles y createdAt
     * @throws ResponseStatusException 409 si el email ya está en uso
     */
    public LoginResponse register(RegisterRequest request) {
        // 1. Verificar unicidad de email
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        // 2. Generar username único desde el prefijo del email
        String username = generateUniqueUsername(request.email());

        // 3. Crear y persistir el usuario SIN roles (UR-R4: admin asigna despues)
        User user = new User(username, request.email(),
                passwordEncoder.encode(request.password()) /* sin roles: varargs vacio */);
        User saved = userRepository.save(user);

        // 4. Generar tokens
        String accessToken = jwtService.generateToken(saved);
        String refreshToken = jwtService.generateToken(saved, refreshExpiration);

        // 5. Construir respuesta con email + username + roles
        return buildLoginResponse(saved, accessToken, refreshToken);
    }

    /**
     * Autentica un usuario por email+password.
     * <p>
     * WHAT: Delega la autenticación a AuthenticationManager (que usa
     * CustomUserDetailsService.loadUserByUsername → findByEmail).
     * Luego busca al usuario por email para construir la respuesta.
     * WHY: Spring Security maneja la validación de credenciales; nosotros
     * solo necesitamos el User para devolver role, email, username y createdAt.
     *
     * @param request DTO con email y password
     * @return LoginResponse con tokens, email, username, role y createdAt
     */
    public LoginResponse login(LoginRequest request) {
        // 1. Autenticar — AuthenticationManager usa CustomUserDetailsService internamente
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // 2. Buscar usuario por email para construir la respuesta
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3. Generar tokens
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateToken(user, refreshExpiration);

        // 4. Construir respuesta con email + username + roles
        return buildLoginResponse(user, accessToken, refreshToken);
    }

    /**
     * Renueva el access token usando un refresh token válido.
     * <p>
     * QUÉ: Valida el refresh token, carga el usuario asociado, y genera
     * un nuevo par de tokens (access + refresh).
     * POR QUÉ: El refresh token tiene una vida más larga que el access token
     * (7 días vs minutos). El cliente lo usa para obtener un nuevo access token
     * sin obligar al usuario a re-login.
     * CÓMO:
     * 1. Extrae el username del token (catch JwtException → MALFORMED)
     * 2. Carga el usuario (catch UsernameNotFoundException → USER_NOT_FOUND)
     * 3. Valida que el token sea válido para ese usuario
     * 4. Genera nuevo par de tokens
     * 5. Construye LoginResponse con los nuevos tokens
     *
     * @param request DTO con el refresh token a validar
     * @return LoginResponse con el nuevo par de tokens + datos del usuario
     * @throws InvalidRefreshTokenException si el token es inválido, expiró,
     *                                      está mal formado, o el usuario no existe
     */
    public LoginResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();

        // 1. Extraer username del token — si está mal formado, JwtException lo indica
        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (JwtException e) {
            throw new InvalidRefreshTokenException(
                    "El refresh token está mal formado",
                    InvalidRefreshTokenException.RefreshError.MALFORMED
            );
        }

        // 2. Cargar el usuario — si fue eliminado, UsernameNotFoundException lo revela
        UserDetails userDetails;
        try {
            userDetails = customUserDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            throw new InvalidRefreshTokenException(
                    "El usuario asociado al refresh token no existe",
                    InvalidRefreshTokenException.RefreshError.USER_NOT_FOUND
            );
        }

        // 3. Validar que el token sea válido para este usuario
        if (!jwtService.isTokenValid(token, userDetails)) {
            throw new InvalidRefreshTokenException(
                    "El refresh token no es válido o ha expirado",
                    InvalidRefreshTokenException.RefreshError.INVALID
            );
        }

        // 4. Generar nuevo par de tokens
        String newAccessToken = jwtService.generateToken(userDetails);
        String newRefreshToken = jwtService.generateToken(userDetails, refreshExpiration);

        // 5. Construir respuesta con email + username + roles
        User user = (User) userDetails;

        return buildLoginResponse(user, newAccessToken, newRefreshToken);
    }

    /**
     * Construye un LoginResponse a partir del usuario y los tokens.
     * <p>
     * WHAT: Mapea los roles del usuario a List<String> para el campo roles
     * y extrae el primer rol (o null) para el campo role deprecated.
     * WHY: Centraliza la construccion de LoginResponse para evitar duplicacion
     * en register(), login() y refresh(). El campo role deprecated se llena
     * con el primer rol para backward compatibility con clientes viejos.
     *
     * @param user        usuario autenticado/registrado
     * @param accessToken JWT access token
     * @param refreshToken JWT refresh token
     * @return LoginResponse con todos los campos poblados
     */
    private LoginResponse buildLoginResponse(User user, String accessToken, String refreshToken) {
        Set<Role> userRoles = user.getRoles();
        List<String> roleStrings = userRoles.stream()
                .map(Enum::name)
                .toList();
        String deprecatedRole = user.getRole() != null ? user.getRole().name() : null;

        return new LoginResponse(
                accessToken,
                refreshToken,
                deprecatedRole,
                user.getEmail(),
                user.getRawUsername(),
                user.getCreatedAt(),
                roleStrings
        );
    }

    /**
     * Genera un username único a partir del prefijo del email.
     * <p>
     * WHAT: Extrae la parte antes del '@' del email. Si ese username no existe,
     * lo usa directamente. Si existe (colisión), agrega un sufijo aleatorio
     * de 4 caracteres alfanuméricos (ej: "juan-4xk9") y reintenta hasta 10 veces.
     * WHY: El username es necesario para display/admin pero el usuario solo
     * provee email. La generación automática evita fricción en el registro.
     *
     * @param email Email del cual extraer el prefijo
     * @return Username único garantizado (no existe en la DB)
     * @throws RuntimeException si no se pudo generar un username único en 10 intentos
     */
    private String generateUniqueUsername(String email) {
        String prefix = email.substring(0, email.indexOf('@'));

        if (!userRepository.existsByUsername(prefix)) {
            return prefix;
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            String suffix = ThreadLocalRandom.current()
                    .ints(4, 0, SUFFIX_ALPHABET.length())
                    .mapToObj(i -> String.valueOf(SUFFIX_ALPHABET.charAt(i)))
                    .collect(Collectors.joining());
            String candidate = prefix + "-" + suffix;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        throw new RuntimeException("Could not generate unique username after 10 attempts");
    }
}
