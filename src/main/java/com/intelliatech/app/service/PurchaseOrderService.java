package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.PurchaseOrderEmailRequest;
import com.intelliatech.app.dto.request.PurchaseOrderReceiveRequest;
import com.intelliatech.app.dto.request.PurchaseOrderRequest;
import com.intelliatech.app.dto.request.PurchaseOrderStatusRequest;
import com.intelliatech.app.dto.response.PurchaseOrderFiltersResponse;
import com.intelliatech.app.dto.response.PurchaseOrderResponse;
import com.intelliatech.app.dto.response.PurchaseOrderSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseOrderService {
    Page<PurchaseOrderResponse> findAll(String search, Long vendorId, String status, String sourceOfSupply,
            String destinationOfSupply, String currency, String gstTreatment, String createdBy,
            LocalDate dateFrom, LocalDate dateTo, LocalDate expectedDeliveryFrom,
            LocalDate expectedDeliveryTo, BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable);
    PurchaseOrderResponse findById(Long id);
    PurchaseOrderSummaryResponse summary();
    PurchaseOrderFiltersResponse filters();
    PurchaseOrderResponse create(PurchaseOrderRequest request, String action);
    PurchaseOrderResponse update(Long id, PurchaseOrderRequest request);
    PurchaseOrderResponse issue(Long id);
    PurchaseOrderResponse receive(Long id, PurchaseOrderReceiveRequest request);
    PurchaseOrderResponse cloneOrder(Long id);
    PurchaseOrderResponse convertToBill(Long id);
    PurchaseOrderResponse sendEmail(Long id, PurchaseOrderEmailRequest request);
    PurchaseOrderResponse cancel(Long id, PurchaseOrderStatusRequest request);
    PurchaseOrderResponse close(Long id, PurchaseOrderStatusRequest request);
    byte[] pdf(Long id);
    void delete(Long id);
}
