package com.ingestion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SummaryResponse(
        long totalTransactions,
        BigDecimal totalVolume,
        long distinctCategories,
        LocalDate earliestDate,
        LocalDate latestDate
) {
}
