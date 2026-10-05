package com.intelliatech.app.dto.response;

import java.time.LocalDateTime;

public record InvoiceReminderResponse(
        Long id,
        String channel,
        String recipient,
        String message,
        LocalDateTime scheduledAt,
        LocalDateTime sentAt,
        String deliveryStatus,
        String createdBy,
        LocalDateTime createdAt
) {
}
