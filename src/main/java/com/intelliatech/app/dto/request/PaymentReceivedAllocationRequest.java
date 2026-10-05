package com.intelliatech.app.dto.request;

import java.math.BigDecimal;

public record PaymentReceivedAllocationRequest(
        Long invoiceId,
        BigDecimal paymentApplied,
        BigDecimal tdsApplied,
        BigDecimal creditApplied
) {
}
