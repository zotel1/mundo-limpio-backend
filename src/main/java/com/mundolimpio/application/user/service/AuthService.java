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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;

    @Value("${application.security.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager, CustomUserDetailsService customUserDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.customUserDetailsService = customUserDetailsService;
    }

    public LoginResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User(request.username(), passwordEncoder.encode(request.password()), Role.OPERATOR);
        User saved = userRepository.save(user);
        String accessToken = jwtService.generateToken(saved);
        String refreshToken = jwtService.generateToken(saved, refreshExpiration);

        return new LoginResponse(accessToken, refreshToken, saved.getRole().name(), saved.getUsername(), saved.getCreatedAt());
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("User not found"));
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateToken(user, refreshExpiration);

        return new LoginResponse(accessToken, refreshToken, user.getRole().name(), user.getUsername(), user.getCreatedAt());
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
        //    isTokenValid compara username + expiración internamente
        if (!jwtService.isTokenValid(token, userDetails)) {
            throw new InvalidRefreshTokenException(
                    "El refresh token no es válido o ha expirado",
                    InvalidRefreshTokenException.RefreshError.INVALID
            );
        }

        // 4. Generar nuevo par de tokens
        String newAccessToken = jwtService.generateToken(userDetails);
        String newRefreshToken = jwtService.generateToken(userDetails, refreshExpiration);

        // 5. Construir respuesta — casteamos a User para obtener role y createdAt
        //    porque CustomUserDetailsService devuelve User (que implementa UserDetails)
        User user = (User) userDetails;

        return new LoginResponse(
                newAccessToken,
                newRefreshToken,
                user.getRole().name(),
                user.getUsername(),
                user.getCreatedAt()
        );
    }
}
