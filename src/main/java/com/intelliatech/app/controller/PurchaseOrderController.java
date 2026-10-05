package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.PurchaseOrderEmailRequest;
import com.intelliatech.app.dto.request.PurchaseOrderReceiveRequest;
import com.intelliatech.app.dto.request.PurchaseOrderRequest;
import com.intelliatech.app.dto.request.PurchaseOrderStatusRequest;
import com.intelliatech.app.dto.response.PurchaseOrderFiltersResponse;
import com.intelliatech.app.dto.response.PurchaseOrderResponse;
import com.intelliatech.app.dto.response.PurchaseOrderSummaryResponse;
import com.intelliatech.app.service.PurchaseOrderService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public Page<PurchaseOrderResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceOfSupply,
            @RequestParam(required = false) String destinationOfSupply,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String gstTreatment,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDeliveryFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDeliveryTo,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = 10, sort = "purchaseOrderDate") Pageable pageable) {
        return purchaseOrderService.findAll(search, vendorId, status, sourceOfSupply, destinationOfSupply,
                currency, gstTreatment, createdBy, dateFrom, dateTo, expectedDeliveryFrom,
                expectedDeliveryTo, minAmount, maxAmount, pageable);
    }

    @GetMapping("/summary")
    public PurchaseOrderSummaryResponse summary() { return purchaseOrderService.summary(); }

    @GetMapping("/filters")
    public PurchaseOrderFiltersResponse filters() { return purchaseOrderService.filters(); }

    @GetMapping("/{id}")
    public PurchaseOrderResponse findById(@PathVariable Long id) { return purchaseOrderService.findById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse create(@Valid @RequestBody PurchaseOrderRequest request,
            @RequestParam(defaultValue = "draft") String action) {
        return purchaseOrderService.create(request, action);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.update(id, request);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse issue(@PathVariable Long id) { return purchaseOrderService.issue(id); }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse receive(@PathVariable Long id, @Valid @RequestBody PurchaseOrderReceiveRequest request) {
        return purchaseOrderService.receive(id, request);
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse cloneOrder(@PathVariable Long id) { return purchaseOrderService.cloneOrder(id); }

    @PostMapping("/{id}/convert-to-bill")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse convertToBill(@PathVariable Long id) { return purchaseOrderService.convertToBill(id); }

    @PostMapping("/{id}/send-email")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse sendEmail(@PathVariable Long id, @Valid @RequestBody PurchaseOrderEmailRequest request) {
        return purchaseOrderService.sendEmail(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse cancel(@PathVariable Long id,
            @Valid @RequestBody(required = false) PurchaseOrderStatusRequest request) {
        return purchaseOrderService.cancel(id, request == null ? new PurchaseOrderStatusRequest(null) : request);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public PurchaseOrderResponse close(@PathVariable Long id,
            @Valid @RequestBody(required = false) PurchaseOrderStatusRequest request) {
        return purchaseOrderService.close(id, request == null ? new PurchaseOrderStatusRequest(null) : request);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        PurchaseOrderResponse order = purchaseOrderService.findById(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("Purchase_Order_" + order.purchaseOrderNumber() + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(purchaseOrderService.pdf(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public void delete(@PathVariable Long id) { purchaseOrderService.delete(id); }
}
