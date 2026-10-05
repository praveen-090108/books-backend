package com.intelliatech.app.dto.response;

import com.intelliatech.app.entity.BillPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BillPaymentResponse(
        Long id, String paymentNumber, LocalDate paymentDate, String paymentMode,
        String paidThrough, Long bankAccountId, String toAccount, String referenceNumber, BigDecimal amount,
        String notes, String attachmentName, String attachmentUrl, BillPaymentStatus status,
        LocalDateTime reversedAt, String reversalReason, String createdBy, LocalDateTime createdAt
) {}
