package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.ApplyInvoiceCreditNoteRequest;
import com.intelliatech.app.dto.request.BusinessRecordRequest;
import com.intelliatech.app.dto.request.InvoiceCommunicationRequest;
import com.intelliatech.app.dto.request.InvoiceReminderRequest;
import com.intelliatech.app.dto.request.RecordInvoicePaymentRequest;
import com.intelliatech.app.dto.request.ReverseInvoicePaymentRequest;
import com.intelliatech.app.dto.request.VoidInvoiceRequest;
import com.intelliatech.app.dto.response.InvoiceLifecycleResponse;
import com.intelliatech.app.dto.response.EligibleInvoiceForCreditNoteResponse;
import com.intelliatech.app.entity.BusinessRecord;
import java.util.Collection;
import java.util.Map;
import java.util.List;

public interface InvoiceLifecycleService {

    void initializeDraft(BusinessRecord invoice);

    InvoiceLifecycleResponse findByInvoiceId(Long invoiceId);

    Map<Long, InvoiceLifecycleResponse> findAllByInvoiceIds(Collection<Long> invoiceIds);

    List<EligibleInvoiceForCreditNoteResponse> findEligibleForCreditNote(Long customerId, Long creditNoteId);

    InvoiceLifecycleResponse markAsSent(Long invoiceId, String recipient);

    InvoiceLifecycleResponse markAsViewed(Long invoiceId);

    InvoiceLifecycleResponse recordCommunication(Long invoiceId, InvoiceCommunicationRequest request);

    InvoiceLifecycleResponse recordPayment(Long invoiceId, RecordInvoicePaymentRequest request);

    InvoiceLifecycleResponse updatePayment(Long invoiceId, Long paymentId, RecordInvoicePaymentRequest request);

    InvoiceLifecycleResponse reversePayment(Long invoiceId, Long paymentId, ReverseInvoicePaymentRequest request);

    InvoiceLifecycleResponse deletePayment(Long invoiceId, Long paymentId);

    InvoiceLifecycleResponse sendReminder(Long invoiceId, InvoiceReminderRequest request);

    InvoiceLifecycleResponse voidInvoice(Long invoiceId, VoidInvoiceRequest request);

    InvoiceLifecycleResponse applyCreditNote(Long invoiceId, Long creditNoteId, ApplyInvoiceCreditNoteRequest request);

    InvoiceLifecycleResponse removeCreditNote(Long invoiceId, Long creditNoteId);

    void assertCanEdit(BusinessRecord invoice, BusinessRecordRequest request);

    void assertCanDelete(Long invoiceId);

    void refreshOverdueStatus(Long invoiceId);

    void refreshAllOverdueStatuses();

    void processScheduledMessages();
}
