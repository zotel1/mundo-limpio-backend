package com.mundolimpio.application.security.service;

import com.mundolimpio.application.user.domain.User;
import com.mundolimpio.application.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implementación de UserDetailsService que busca usuarios por email.
 * <p>
 * WHAT: loadUserByUsername(String email) ahora llama a findByEmail(email)
 * en lugar de findByUsername(email).
 * WHY: Spring Security pasa "username" pero nosotros interpretamos como email.
 * User.getUsername() devuelve email, y JwtAuthenticationFilter extrae el email
 * del JWT "sub" y lo pasa a este método. El lookup debe ser por email.
 * DIFFERENCES: Antes llamaba a findByUsername(username). Ahora findByEmail(email).
 * La firma del método no cambia — solo la implementación interna.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return user;
    }
}
