package com.intelliatech.app.dto.response;

import java.math.BigDecimal;

public record VendorSummaryResponse(
        long totalVendors,
        long activeVendors,
        long inactiveVendors,
        BigDecimal totalPayables,
        BigDecimal totalOverdue,
        BigDecimal paidThisMonth,
        BigDecimal purchasesThisMonth
) {
}
