package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.ApplyInvoiceCreditNoteRequest;
import com.intelliatech.app.dto.request.InvoiceCommunicationRequest;
import com.intelliatech.app.dto.request.InvoiceReminderRequest;
import com.intelliatech.app.dto.request.RecordInvoicePaymentRequest;
import com.intelliatech.app.dto.request.ReverseInvoicePaymentRequest;
import com.intelliatech.app.dto.request.VoidInvoiceRequest;
import com.intelliatech.app.dto.response.InvoiceLifecycleResponse;
import com.intelliatech.app.dto.response.EligibleInvoiceForCreditNoteResponse;
import com.intelliatech.app.service.InvoiceLifecycleService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceLifecycleController {

    private final InvoiceLifecycleService service;

    @GetMapping("/lifecycle")
    public Map<Long, InvoiceLifecycleResponse> findAll(@RequestParam List<Long> ids) {
        return service.findAllByInvoiceIds(ids);
    }

    @GetMapping("/eligible-for-credit-note")
    public List<EligibleInvoiceForCreditNoteResponse> findEligibleForCreditNote(
            @RequestParam Long customerId,
            @RequestParam(required = false) Long creditNoteId
    ) {
        return service.findEligibleForCreditNote(customerId, creditNoteId);
    }

    @GetMapping("/{invoiceId}/lifecycle")
    public InvoiceLifecycleResponse findById(@PathVariable Long invoiceId) {
        return service.findByInvoiceId(invoiceId);
    }

    @PostMapping("/{invoiceId}/mark-sent")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'SALES')")
    public InvoiceLifecycleResponse markSent(@PathVariable Long invoiceId, @RequestParam(required = false) String recipient) {
        return service.markAsSent(invoiceId, recipient);
    }

    @PostMapping("/{invoiceId}/viewed")
    public InvoiceLifecycleResponse markViewed(@PathVariable Long invoiceId) {
        return service.markAsViewed(invoiceId);
    }

    @PostMapping("/{invoiceId}/communications")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'SALES')")
    public InvoiceLifecycleResponse communicate(@PathVariable Long invoiceId, @RequestBody InvoiceCommunicationRequest request) {
        return service.recordCommunication(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public InvoiceLifecycleResponse recordPayment(@PathVariable Long invoiceId, @RequestBody RecordInvoicePaymentRequest request) {
        return service.recordPayment(invoiceId, request);
    }

    @PutMapping("/{invoiceId}/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public InvoiceLifecycleResponse updatePayment(
            @PathVariable Long invoiceId,
            @PathVariable Long paymentId,
            @RequestBody RecordInvoicePaymentRequest request
    ) {
        return service.updatePayment(invoiceId, paymentId, request);
    }

    @PostMapping("/{invoiceId}/payments/{paymentId}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public InvoiceLifecycleResponse reversePayment(
            @PathVariable Long invoiceId,
            @PathVariable Long paymentId,
            @RequestBody ReverseInvoicePaymentRequest request
    ) {
        return service.reversePayment(invoiceId, paymentId, request);
    }

    @DeleteMapping("/{invoiceId}/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public InvoiceLifecycleResponse deletePayment(
            @PathVariable Long invoiceId,
            @PathVariable Long paymentId
    ) {
        return service.deletePayment(invoiceId, paymentId);
    }

    @PostMapping("/{invoiceId}/reminders")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'SALES')")
    public InvoiceLifecycleResponse reminder(@PathVariable Long invoiceId, @RequestBody InvoiceReminderRequest request) {
        return service.sendReminder(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/void")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public InvoiceLifecycleResponse voidInvoice(@PathVariable Long invoiceId, @RequestBody VoidInvoiceRequest request) {
        return service.voidInvoice(invoiceId, request);
    }

    @PostMapping("/{invoiceId}/credit-notes/{creditNoteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public InvoiceLifecycleResponse applyCreditNote(
            @PathVariable Long invoiceId,
            @PathVariable Long creditNoteId,
            @RequestBody ApplyInvoiceCreditNoteRequest request
    ) {
        return service.applyCreditNote(invoiceId, creditNoteId, request);
    }

    @DeleteMapping("/{invoiceId}/credit-notes/{creditNoteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public InvoiceLifecycleResponse removeCreditNote(
            @PathVariable Long invoiceId,
            @PathVariable Long creditNoteId
    ) {
        return service.removeCreditNote(invoiceId, creditNoteId);
    }
}
