package com.intelliatech.app.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordInvoicePaymentRequest(
        BigDecimal amountReceived,
        LocalDate paymentDate,
        String paymentMode,
        String depositAccount,
        Long bankAccountId,
        String referenceNumber,
        Boolean tdsDeducted,
        BigDecimal tdsPercentage,
        BigDecimal tdsAmount,
        String tdsBaseType,
        String tdsSectionCode,
        String tdsCertificateNumber,
        LocalDate tdsCertificateDate,
        String tdsRemarks,
        BigDecimal bankCharges,
        BigDecimal creditApplied,
        String notes,
        String attachmentUrl,
        Boolean sendThankYouEmail,
        String idempotencyKey
) {
    public RecordInvoicePaymentRequest(BigDecimal amountReceived, LocalDate paymentDate, String paymentMode,
            String depositAccount, String referenceNumber, Boolean tdsDeducted, BigDecimal tdsPercentage,
            BigDecimal tdsAmount, String tdsBaseType, String tdsSectionCode, String tdsCertificateNumber,
            LocalDate tdsCertificateDate, String tdsRemarks, BigDecimal bankCharges, BigDecimal creditApplied,
            String notes, String attachmentUrl, Boolean sendThankYouEmail, String idempotencyKey) {
        this(amountReceived, paymentDate, paymentMode, depositAccount, null, referenceNumber, tdsDeducted,
                tdsPercentage, tdsAmount, tdsBaseType, tdsSectionCode, tdsCertificateNumber,
                tdsCertificateDate, tdsRemarks, bankCharges, creditApplied, notes, attachmentUrl,
                sendThankYouEmail, idempotencyKey);
    }
}
