package com.ingestion.config;

/**
 * Shape compartilhado por todo limite token-bucket por IP no app (uploads,
 * auth). Não é uma classe @ConfigurationProperties escaneada: é bindada duas
 * vezes, pra dois prefixos diferentes, via os @Bean explícitos em
 * {@link RateLimitConfig}.
 */
public record RateLimitProperties(
        int capacity,
        double refillPerMinute
) {
}
