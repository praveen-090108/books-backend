package com.intelliatech.app.dto.request;

import java.time.LocalDateTime;

public record InvoiceReminderRequest(
        String channel,
        String recipient,
        String message,
        LocalDateTime scheduledAt,
        Boolean deliverySuccessful,
        String failureReason
) {
}
