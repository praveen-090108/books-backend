package com.intelliatech.app.dto.response;

import java.math.BigDecimal;

public record BillSummaryResponse(
        long totalBills, long draftBills, long openBills, long partiallyPaidBills,
        long paidBills, long overdueBills, BigDecimal totalAmount,
        BigDecimal amountPaid, BigDecimal balanceDue
) {}

