package com.mundolimpio.application.security.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WHAT: Configuración de rate limiting con bucket4j.
 * WHY: Proteger endpoints públicos (auth) de abusos y bruteforce.
 *      Cada IP tiene su propio bucket con tokens que se regeneran con el tiempo.
 */
@Configuration
public class RateLimitConfig {

    @Value("${app.rate-limit.auth-max-requests:10}")
    private int authMaxRequests;

    @Value("${app.rate-limit.auth-time-window-minutes:1}")
    private int authTimeWindowMinutes;

    @Value("${app.rate-limit.default-max-requests:100}")
    private int defaultMaxRequests;

    @Value("${app.rate-limit.default-time-window-minutes:1}")
    private int defaultTimeWindowMinutes;

    /**
     * Cache de buckets por IP.
     * ConcurrentHashMap es thread-safe y no bloquea lecturas.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Obtiene o crea un bucket para una IP con límite de auth endpoints.
     * 10 requests por minuto — suficiente para login/register normal.
     */
    public Bucket resolveAuthBucket(String ip) {
        return buckets.computeIfAbsent(ip + "-auth", k -> {
            Bandwidth limit = Bandwidth.classic(authMaxRequests,
                    Refill.intervally(authMaxRequests, Duration.ofMinutes(authTimeWindowMinutes)));
            return Bucket.builder().addLimit(limit).build();
        });
    }

    /**
     * Obtiene o crea un bucket para una IP con límite general.
     * 100 requests por minuto — cubre navegación normal.
     */
    public Bucket resolveDefaultBucket(String ip) {
        return buckets.computeIfAbsent(ip, k -> {
            Bandwidth limit = Bandwidth.classic(defaultMaxRequests,
                    Refill.intervally(defaultMaxRequests, Duration.ofMinutes(defaultTimeWindowMinutes)));
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
