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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket por IP na frente de POST /api/uploads. Cada upload pode chegar a 2GB
 * e dispara um job de ingestão em background — é o endpoint mais exposto a esgotar
 * disco/DB/thread-pool se um caller mandar requests mais rápido do que o pipeline
 * consegue drenar.
 *
 * Chave é request.getRemoteAddr() — ver AuthRateLimitFilter pra por que dá pra
 * confiar nisso como IP real do cliente nesse deployment.
 */
@Component
public class UploadRateLimitFilter extends OncePerRequestFilter {

    private static final String LIMITED_PATH = "/api/uploads";

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public UploadRateLimitFilter(@Qualifier("uploadRateLimitProperties") RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        TokenBucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(),
                key -> new TokenBucket(properties.capacity(), properties.refillPerMinute()));

        if (!bucket.tryConsume()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many upload requests, slow down.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
