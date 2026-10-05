package com.intelliatech.app.dto.response;

import java.math.BigDecimal;

public record ExpenseTaxSummaryResponse(
        BigDecimal enteredAmount,
        BigDecimal taxableAmount,
        BigDecimal taxRate,
        String taxMode,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal cessAmount,
        BigDecimal totalTaxAmount,
        BigDecimal totalAmount
) {}
