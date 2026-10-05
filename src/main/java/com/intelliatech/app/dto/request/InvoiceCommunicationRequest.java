package com.intelliatech.app.dto.request;

import java.time.LocalDateTime;

public record InvoiceCommunicationRequest(
        String type,
        String recipient,
        String cc,
        String bcc,
        String subject,
        String body,
        LocalDateTime scheduledAt,
        Boolean deliverySuccessful,
        String failureReason
) {
}
