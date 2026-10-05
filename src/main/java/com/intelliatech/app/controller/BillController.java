package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.*;
import com.intelliatech.app.dto.response.*;
import com.intelliatech.app.service.BillService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {
    private final BillService billService;

    @GetMapping
    public Page<BillResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = 10, sort = "billDate") Pageable pageable) {
        return billService.findAll(search, vendorId, status, paymentStatus, billDateFrom, billDateTo,
                dueDateFrom, dueDateTo, minAmount, maxAmount, pageable);
    }

    @GetMapping("/summary") public BillSummaryResponse summary() { return billService.summary(); }
    @GetMapping("/filters") public BillFiltersResponse filters() { return billService.filters(); }
    @GetMapping("/{id}") public BillResponse findById(@PathVariable Long id) { return billService.findById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse create(@Valid @RequestBody BillRequest request, @RequestParam(defaultValue = "draft") String action) {
        return billService.create(request, action);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse update(@PathVariable Long id, @Valid @RequestBody BillRequest request,
            @RequestParam(defaultValue = "draft") String action) {
        return billService.update(id, request, action);
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse convertToOpen(@PathVariable Long id) { return billService.convertToOpen(id); }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse recordPayment(@PathVariable Long id, @Valid @RequestBody BillPaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return billService.recordPayment(id, request, idempotencyKey);
    }

    @PutMapping("/{id}/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse updatePayment(@PathVariable Long id, @PathVariable Long paymentId,
            @Valid @RequestBody BillPaymentRequest request) {
        return billService.updatePayment(id, paymentId, request);
    }

    @PostMapping("/{id}/payments/{paymentId}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse reversePayment(@PathVariable Long id, @PathVariable Long paymentId,
            @RequestBody(required = false) BillActionRequest request) {
        return billService.reversePayment(id, paymentId, request == null ? new BillActionRequest(null, null) : request);
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse cloneBill(@PathVariable Long id) { return billService.cloneBill(id); }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse voidBill(@PathVariable Long id, @RequestBody(required = false) BillActionRequest request) {
        return billService.voidBill(id, request == null ? new BillActionRequest(null, null) : request);
    }

    @PostMapping("/{id}/expected-payment-date")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public BillResponse expectedPaymentDate(@PathVariable Long id, @Valid @RequestBody BillActionRequest request) {
        return billService.expectedPaymentDate(id, request);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        BillResponse bill = billService.findById(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("Bill_" + bill.billNumber() + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(billService.pdf(id));
    }

    @GetMapping(value = "/{id}/payments/{paymentId}/receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> paymentReceipt(@PathVariable Long id, @PathVariable Long paymentId) {
        BillResponse bill = billService.findById(id);
        BillPaymentResponse payment = bill.payments().stream()
                .filter(item -> item.id().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for this Bill."));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("Payment_Receipt_" + payment.paymentNumber() + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(billService.paymentReceiptPdf(id, paymentId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public void delete(@PathVariable Long id) { billService.delete(id); }
}
