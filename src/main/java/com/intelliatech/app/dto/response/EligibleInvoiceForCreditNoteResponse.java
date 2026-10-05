package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record EligibleInvoiceForCreditNoteResponse(
        Long id,
        Long customerId,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String customerName,
        String customerEmail,
        String customerPhone,
        String customerCity,
        String billingAddress,
        String customerCountry,
        String customerState,
        String currency,
        String status,
        BigDecimal totalAmount,
        BigDecimal balanceDue,
        BigDecimal previouslyCreditedAmount,
        BigDecimal remainingEligibleAmount,
        Map<String, BigDecimal> previouslyCreditedQuantities,
        String notes
) {
}
