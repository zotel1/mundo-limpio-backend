package com.mundolimpio.application.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundolimpio.application.common.dto.ErrorResponse;
import com.mundolimpio.application.security.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * WHAT: Filtro de rate limiting que limita requests por IP.
 * WHY: Protege la API de abusos, bruteforce y DoS.
 *      Los buckets se recargan automáticamente con el tiempo.
 *
 * Límites:
 * - /api/v1/auth/** → 10 requests/minuto por IP (login, register)
 * - Resto → 100 requests/minuto por IP
 */
@Component
@Order(1)  // Ejecutar ANTES que el filtro JWT
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitConfig rateLimitConfig;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitConfig rateLimitConfig, ObjectMapper objectMapper) {
        this.rateLimitConfig = rateLimitConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String clientIp = getClientIp(request);
        String path = request.getRequestURI();

        // Elegir bucket según el path
        Bucket bucket;
        boolean isAuthPath = path.startsWith("/api/v1/auth/");

        if (isAuthPath) {
            bucket = rateLimitConfig.resolveAuthBucket(clientIp);
        } else {
            bucket = rateLimitConfig.resolveDefaultBucket(clientIp);
        }

        // Intentar consumir un token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Agregar headers de rate limit para transparencia
            response.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            // Rate limit excedido
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;

            log.warn("Rate limit exceeded for IP: {} on path: {}. Retry after {} seconds",
                    clientIp, path, waitSeconds);

            response.setStatus(429);
            response.setHeader("Content-Type", "application/json");
            response.setHeader("Retry-After", String.valueOf(waitSeconds));

            String message = "Demasiadas requests. Intenta de nuevo en " + waitSeconds + " segundos.";
            ErrorResponse errorResponse = new ErrorResponse(
                    "RATE_LIMIT_EXCEEDED",
                    message,
                    LocalDateTime.now(),
                    request.getRequestURI()
            );
            String json = objectMapper.writeValueAsString(errorResponse);
            response.getWriter().write(json);
        }
    }

    /**
     * Obtiene la IP real del cliente, considerando proxies.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
