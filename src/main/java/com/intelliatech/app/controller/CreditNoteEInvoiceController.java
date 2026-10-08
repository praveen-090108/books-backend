package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.CancelIrnRequest;
import com.intelliatech.app.dto.response.EInvoiceResponse;
import com.intelliatech.app.service.EInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit-notes")
@RequiredArgsConstructor
public class CreditNoteEInvoiceController {

    private final EInvoiceService eInvoiceService;

    @GetMapping("/{creditNoteId}/e-invoice")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'SALES')")
    public EInvoiceResponse find(@PathVariable Long creditNoteId) {
        return eInvoiceService.findByCreditNoteId(creditNoteId);
    }

    @PostMapping("/{creditNoteId}/e-invoice/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public EInvoiceResponse generate(@PathVariable Long creditNoteId) {
        return eInvoiceService.generateCreditNote(creditNoteId);
    }

    @PostMapping("/{creditNoteId}/e-invoice/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public EInvoiceResponse cancel(@PathVariable Long creditNoteId, @Valid @RequestBody CancelIrnRequest request) {
        return eInvoiceService.cancelCreditNote(creditNoteId, request);
    }

    @PostMapping("/{creditNoteId}/e-invoice/refresh")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public EInvoiceResponse refresh(@PathVariable Long creditNoteId) {
        return eInvoiceService.refreshCreditNote(creditNoteId);
    }
}
