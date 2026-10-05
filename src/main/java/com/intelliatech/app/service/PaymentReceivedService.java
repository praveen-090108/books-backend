package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.PaymentReceivedRequest;
import com.intelliatech.app.dto.request.ReverseInvoicePaymentRequest;
import com.intelliatech.app.dto.response.EligibleInvoiceForPaymentResponse;
import com.intelliatech.app.dto.response.PaymentReceivedResponse;
import com.intelliatech.app.dto.response.PaymentReceivedSummaryResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

public interface PaymentReceivedService {

    List<EligibleInvoiceForPaymentResponse> findEligibleInvoices(Long customerId, Long paymentId);

    PaymentReceivedSummaryResponse summary();

    Page<PaymentReceivedResponse> findAll(
            int page,
            int size,
            String search,
            Long customerId,
            String invoiceNumber,
            LocalDate fromDate,
            LocalDate toDate,
            String paymentMode,
            Boolean tds,
            String status,
            String sortBy,
            String sortDirection
    );

    PaymentReceivedResponse findById(Long id);

    PaymentReceivedResponse create(PaymentReceivedRequest request);

    PaymentReceivedResponse update(Long id, PaymentReceivedRequest request);

    PaymentReceivedResponse reverse(Long id, ReverseInvoicePaymentRequest request);

    void delete(Long id);
}
