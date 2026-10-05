package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentReceivedSummaryResponse(
        BigDecimal totalReceived,
        BigDecimal thisMonthReceived,
        String monthLabel,
        LocalDate monthStart,
        LocalDate monthEnd,
        BigDecimal thisYearReceived,
        String yearLabel,
        LocalDate yearStart,
        LocalDate yearEnd,
        BigDecimal overdueAmount,
        long overdueInvoiceCount
) {
}
