package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.VendorRequest;
import com.intelliatech.app.dto.request.VendorStatementEmailRequest;
import com.intelliatech.app.dto.request.VendorStatusRequest;
import com.intelliatech.app.dto.response.VendorResponse;
import com.intelliatech.app.dto.response.VendorStatementResponse;
import com.intelliatech.app.dto.response.VendorSummaryResponse;
import com.intelliatech.app.dto.response.VendorTransactionResponse;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VendorService {

    Page<VendorResponse> findAll(
            String search,
            String status,
            String payableStatus,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    );

    VendorResponse findById(Long id);

    VendorResponse create(VendorRequest request);

    VendorResponse update(Long id, VendorRequest request);

    VendorResponse updateStatus(Long id, VendorStatusRequest request);

    VendorResponse cloneVendor(Long id);

    void delete(Long id);

    VendorSummaryResponse summary();

    Page<VendorTransactionResponse> transactions(
            Long id,
            String search,
            String transactionType,
            String status,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    );

    VendorStatementResponse statement(Long id, LocalDate dateFrom, LocalDate dateTo, String transactionType);

    byte[] statementPdf(Long id, LocalDate dateFrom, LocalDate dateTo, String transactionType);

    byte[] statementExcel(Long id, LocalDate dateFrom, LocalDate dateTo, String transactionType);

    Map<String, String> emailStatement(
            Long id,
            LocalDate dateFrom,
            LocalDate dateTo,
            String transactionType,
            VendorStatementEmailRequest request
    );
}
