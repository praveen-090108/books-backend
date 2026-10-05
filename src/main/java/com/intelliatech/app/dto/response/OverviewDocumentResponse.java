package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OverviewDocumentResponse(
        Long id,
        String number,
        String partyName,
        LocalDate documentDate,
        LocalDate dueDate,
        BigDecimal amount,
        BigDecimal balance,
        String status
) {
}
