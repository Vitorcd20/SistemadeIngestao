package com.ingestion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRow(
        long id,
        LocalDate transactionDate,
        String category,
        BigDecimal amount,
        String description
) {
}
