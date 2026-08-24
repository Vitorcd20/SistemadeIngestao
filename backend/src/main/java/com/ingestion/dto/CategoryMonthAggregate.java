package com.ingestion.dto;

import java.math.BigDecimal;

public record CategoryMonthAggregate(
        String category,
        String month,
        BigDecimal totalAmount,
        long transactionCount
) {
}
