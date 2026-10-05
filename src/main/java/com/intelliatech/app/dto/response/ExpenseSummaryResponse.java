package com.intelliatech.app.dto.response;

import java.math.BigDecimal;

public record ExpenseSummaryResponse(
        BigDecimal totalExpenses,
        BigDecimal thisMonth,
        BigDecimal thisYear,
        long totalCount
) {}
