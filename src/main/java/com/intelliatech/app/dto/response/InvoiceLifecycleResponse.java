package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceLifecycleResponse(
        Long invoiceId,
        String invoiceNumber,
        String customerName,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        String status,
        BigDecimal invoiceTotal,
        BigDecimal cashAmountPaid,
        BigDecimal tdsSettled,
        BigDecimal creditApplied,
        BigDecimal creditNoteApplied,
        BigDecimal totalSettled,
        BigDecimal balanceDue,
        LocalDateTime sentAt,
        String sentRecipient,
        LocalDateTime firstViewedAt,
        LocalDateTime lastViewedAt,
        LocalDateTime paidAt,
        LocalDateTime voidedAt,
        String voidReason,
        long overdueDays,
        InvoiceActionPermissionsResponse actions,
        List<InvoicePaymentResponse> payments,
        List<InvoiceReminderResponse> reminders,
        List<InvoiceCommunicationResponse> communications,
        List<InvoiceCreditNoteLinkResponse> creditNotes
) {
}
