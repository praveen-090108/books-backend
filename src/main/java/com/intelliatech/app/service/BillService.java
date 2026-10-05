package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.*;
import com.intelliatech.app.dto.response.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BillService {
    Page<BillResponse> findAll(String search, Long vendorId, String status, String paymentStatus,
            LocalDate billDateFrom, LocalDate billDateTo, LocalDate dueDateFrom, LocalDate dueDateTo,
            BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable);
    BillSummaryResponse summary();
    BillFiltersResponse filters();
    BillResponse findById(Long id);
    BillResponse create(BillRequest request, String action);
    BillResponse update(Long id, BillRequest request, String action);
    BillResponse convertToOpen(Long id);
    BillResponse recordPayment(Long id, BillPaymentRequest request, String idempotencyKey);
    BillResponse updatePayment(Long id, Long paymentId, BillPaymentRequest request);
    BillResponse reversePayment(Long id, Long paymentId, BillActionRequest request);
    BillResponse cloneBill(Long id);
    BillResponse voidBill(Long id, BillActionRequest request);
    BillResponse expectedPaymentDate(Long id, BillActionRequest request);
    byte[] pdf(Long id);
    byte[] paymentReceiptPdf(Long id, Long paymentId);
    void delete(Long id);
}
