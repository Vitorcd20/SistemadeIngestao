package com.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        int batchSize,
        int errorSampleLimit,
        String tempDir,
        long maxFileSizeBytes
) {
}
