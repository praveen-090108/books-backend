package com.intelliatech.app.dto.response;

import java.time.LocalDateTime;

public record InvoiceCommunicationResponse(
        Long id,
        String type,
        String recipient,
        String cc,
        String bcc,
        String subject,
        String body,
        LocalDateTime scheduledAt,
        LocalDateTime sentAt,
        String deliveryStatus,
        String failureReason,
        String createdBy,
        LocalDateTime createdAt
) {
}
