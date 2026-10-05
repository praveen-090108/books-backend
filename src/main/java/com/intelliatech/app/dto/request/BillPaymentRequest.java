package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BillPaymentRequest(
        @NotNull(message = "Payment date is required") LocalDate paymentDate,
        @NotNull(message = "Payment amount is required") @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero") BigDecimal amount,
        @NotBlank(message = "Payment mode is required") @Size(max = 64) String paymentMode,
        @Size(max = 160) String paidThrough,
        Long bankAccountId,
        @Size(max = 160) String toAccount,
        @Size(max = 120) String referenceNumber,
        @Size(max = 2000) String notes,
        @Size(max = 255) String attachmentName,
        @Size(max = 1000) String attachmentUrl
) {}
