package com.intelliatech.app.dto.response;

import java.math.BigDecimal;

public record VendorFinancialSummaryResponse(
        BigDecimal totalPurchases,
        BigDecimal totalPaid,
        BigDecimal outstandingPayables,
        BigDecimal overdueAmount,
        BigDecimal unusedCredits
) {
}
