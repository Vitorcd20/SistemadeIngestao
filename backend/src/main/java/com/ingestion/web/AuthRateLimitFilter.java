package com.ingestion.web;

import com.ingestion.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket por IP na frente de /api/auth/login e /api/auth/register. Os
 * dois são permitAll no SecurityConfig (ainda não tem cookie de sessão pra
 * usar como chave), então isso é a única coisa entre eles e tentativas
 * ilimitadas de credential-stuffing / spam de registro.
 *
 * Chave é request.getRemoteAddr() — ver UploadRateLimitFilter pra entender
 * por que dá pra confiar nisso como IP real do cliente nesse deployment (sem
 * porta do backend publicada + forward-headers-strategy=framework).
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(@Qualifier("authRateLimitProperties") RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        TokenBucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(),
                key -> new TokenBucket(properties.capacity(), properties.refillPerMinute()));

        if (!bucket.tryConsume()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many attempts, slow down.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
