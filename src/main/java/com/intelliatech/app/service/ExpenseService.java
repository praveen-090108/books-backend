package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.ExpenseRequest;
import com.intelliatech.app.dto.response.ExpenseFiltersResponse;
import com.intelliatech.app.dto.response.ExpenseResponse;
import com.intelliatech.app.dto.response.ExpenseSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExpenseService {
    Page<ExpenseResponse> findAll(
            String search, Long vendorId, String expenseType, String gstTreatment,
            String sourceOfSupply, String destinationOfSupply, Long taxId, String amountType,
            String expenseAccount, String status, String paymentMode, LocalDate dateFrom,
            LocalDate dateTo, BigDecimal amountMin, BigDecimal amountMax, Pageable pageable);
    ExpenseResponse findById(Long id);
    ExpenseResponse create(ExpenseRequest request);
    ExpenseResponse update(Long id, ExpenseRequest request);
    void delete(Long id);
    ExpenseSummaryResponse summary();
    ExpenseFiltersResponse filters(String gstTreatment, String sourceOfSupply, String destinationOfSupply);
}
