package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

public record InvoiceCreditNoteLinkResponse(
        Long id,
        Long creditNoteId,
        String creditNoteNumber,
        LocalDate creditNoteDate,
        BigDecimal creditNoteAmount,
        String eInvoiceStatus,
        BigDecimal amountApplied,
        BigDecimal remainingBalance,
        LocalDateTime createdAt
) {
}
