package com.intelliatech.app.service;

import com.intelliatech.app.dto.response.EInvoiceResponse;
import com.intelliatech.app.dto.request.CancelIrnRequest;

public interface EInvoiceService {
    EInvoiceResponse findByInvoiceId(Long invoiceId);
    EInvoiceResponse generate(Long invoiceId);
    EInvoiceResponse cancel(Long invoiceId, CancelIrnRequest request);
    EInvoiceResponse refresh(Long invoiceId);
    EInvoiceResponse findByCreditNoteId(Long creditNoteId);
    EInvoiceResponse generateCreditNote(Long creditNoteId);
    EInvoiceResponse cancelCreditNote(Long creditNoteId, CancelIrnRequest request);
    EInvoiceResponse refreshCreditNote(Long creditNoteId);
}
