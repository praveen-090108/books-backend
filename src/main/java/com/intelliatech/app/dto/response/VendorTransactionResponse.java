package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendorTransactionResponse(
        Long id,
        String transactionType,
        String transactionNumber,
        String referenceNumber,
        LocalDate date,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balance,
        String status,
        String viewPath
) {
}
