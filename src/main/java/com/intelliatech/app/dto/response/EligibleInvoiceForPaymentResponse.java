package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EligibleInvoiceForPaymentResponse(
        Long invoiceId,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String status,
        BigDecimal invoiceAmount,
        BigDecimal amountPaid,
        BigDecimal tdsApplied,
        BigDecimal creditsApplied,
        BigDecimal creditNotesApplied,
        BigDecimal balanceDue,
        BigDecimal tdsBase,
        String currency
) {
}
