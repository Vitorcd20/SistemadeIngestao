package com.ingestion.dto;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionRow> items,
        String nextCursor,
        boolean hasMore
) {
}
