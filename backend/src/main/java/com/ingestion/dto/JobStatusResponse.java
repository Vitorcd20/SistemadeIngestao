package com.ingestion.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record JobStatusResponse(
        UUID id,
        String fileName,
        String status,
        long rowsProcessed,
        long rowsFailed,
        List<String> errorSample,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt
) {
}
