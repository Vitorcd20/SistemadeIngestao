package com.ingestion.dto;

import java.util.UUID;

public record UploadResponse(
        UUID jobId,
        String status,
        String message
) {
}
