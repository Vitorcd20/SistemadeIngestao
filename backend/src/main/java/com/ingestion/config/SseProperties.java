package com.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sse")
public record SseProperties(
        long pollIntervalMs,
        long timeoutMs
) {
}
