package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.PaymentReceivedRequest;
import com.intelliatech.app.dto.request.ReverseInvoicePaymentRequest;
import com.intelliatech.app.dto.response.EligibleInvoiceForPaymentResponse;
import com.intelliatech.app.dto.response.PaymentReceivedResponse;
import com.intelliatech.app.dto.response.PaymentReceivedSummaryResponse;
import com.intelliatech.app.service.PaymentReceivedService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments-received")
@RequiredArgsConstructor
public class PaymentReceivedController {

    private final PaymentReceivedService service;

    @GetMapping("/eligible-invoices")
    public List<EligibleInvoiceForPaymentResponse> eligibleInvoices(
            @RequestParam Long customerId,
            @RequestParam(required = false) Long paymentId
    ) {
        return service.findEligibleInvoices(customerId, paymentId);
    }

    @GetMapping("/summary")
    public PaymentReceivedSummaryResponse summary() {
        return service.summary();
    }

    @GetMapping
    public Page<PaymentReceivedResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) Boolean tds,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        return service.findAll(page, size, search, customerId, invoiceNumber, fromDate, toDate,
                paymentMode, tds, status, sortBy, sortDirection);
    }

    @GetMapping("/{id}")
    public PaymentReceivedResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public PaymentReceivedResponse create(@RequestBody PaymentReceivedRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public PaymentReceivedResponse update(@PathVariable Long id, @RequestBody PaymentReceivedRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public PaymentReceivedResponse reverse(@PathVariable Long id, @RequestBody ReverseInvoicePaymentRequest request) {
        return service.reverse(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
