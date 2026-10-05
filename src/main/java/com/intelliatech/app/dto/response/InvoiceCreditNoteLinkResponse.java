package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceCreditNoteLinkResponse(
        Long id,
        Long creditNoteId,
        String creditNoteNumber,
        BigDecimal amountApplied,
        LocalDateTime createdAt
) {
}
