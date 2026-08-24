package com.ingestion.config;

public record RateLimitProperties(
        int capacity,
        double refillPerMinute
) {
}
