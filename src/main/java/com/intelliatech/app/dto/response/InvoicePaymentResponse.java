package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoicePaymentResponse(
        Long id,
        String paymentNumber,
        String receiptNumber,
        BigDecimal receiptRemainingBalance,
        String currencyCode,
        LocalDate paymentDate,
        String paymentMode,
        String depositAccount,
        Long bankAccountId,
        String referenceNumber,
        String status,
        BigDecimal grossAmountReceived,
        BigDecimal tdsAmount,
        BigDecimal creditApplied,
        BigDecimal bankCharges,
        BigDecimal netBankCredit,
        String tdsBaseType,
        BigDecimal tdsBaseAmount,
        BigDecimal tdsPercentage,
        String tdsSectionCode,
        String tdsCertificateNumber,
        LocalDate tdsCertificateDate,
        String tdsRemarks,
        String notes,
        String attachmentUrl,
        boolean sendThankYouEmail,
        boolean reconciled,
        boolean reversed,
        LocalDateTime reversedAt,
        String reversalReason,
        String createdBy,
        LocalDateTime createdAt
) {
}
