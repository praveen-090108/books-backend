package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record RecordSummaryResponse(
        long totalRecords,
        BigDecimal totalAmount,
        BigDecimal totalBalance,
        Map<String, Long> statusCounts,
        Map<String, BigDecimal> statusAmounts
) {
}
