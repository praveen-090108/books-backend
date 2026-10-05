package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentReceivedAllocationResponse(
        Long invoiceId,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal invoiceTotal,
        BigDecimal paymentApplied,
        BigDecimal tdsApplied,
        BigDecimal creditApplied,
        BigDecimal balanceAfterPayment
) {
}
