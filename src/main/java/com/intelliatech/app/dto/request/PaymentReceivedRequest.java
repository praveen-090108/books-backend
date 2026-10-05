package com.intelliatech.app.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentReceivedRequest(
        Long customerId,
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
        String notes,
        String attachmentUrl,
        Boolean sendThankYouEmail,
        String bankAccountName,
        String bankName,
        String maskedAccountNumber,
        String transactionId,
        String chequeNumber,
        LocalDate chequeDate,
        List<PaymentReceivedAllocationRequest> allocations,
        String idempotencyKey
) {
}
