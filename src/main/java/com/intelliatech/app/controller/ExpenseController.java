package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.ExpenseRequest;
import com.intelliatech.app.dto.response.ExpenseFiltersResponse;
import com.intelliatech.app.dto.response.ExpenseResponse;
import com.intelliatech.app.dto.response.ExpenseSummaryResponse;
import com.intelliatech.app.service.ExpenseService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;

    @GetMapping
    public Page<ExpenseResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String gstTreatment,
            @RequestParam(required = false) String sourceOfSupply,
            @RequestParam(required = false) String destinationOfSupply,
            @RequestParam(required = false) Long taxId,
            @RequestParam(required = false) String amountType,
            @RequestParam(required = false) String expenseAccount,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @PageableDefault(size = 10, sort = "expenseDate") Pageable pageable
    ) {
        return expenseService.findAll(search, vendorId, expenseType, gstTreatment, sourceOfSupply,
                destinationOfSupply, taxId, amountType, expenseAccount, status, paymentMode,
                dateFrom, dateTo, amountMin, amountMax, pageable);
    }

    @GetMapping("/summary")
    public ExpenseSummaryResponse summary() { return expenseService.summary(); }

    @GetMapping("/filters")
    public ExpenseFiltersResponse filters(
            @RequestParam(required = false) String gstTreatment,
            @RequestParam(required = false) String sourceOfSupply,
            @RequestParam(required = false) String destinationOfSupply) {
        return expenseService.filters(gstTreatment, sourceOfSupply, destinationOfSupply);
    }

    @GetMapping("/{id}")
    public ExpenseResponse findById(@PathVariable Long id) { return expenseService.findById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request) { return expenseService.create(request); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public void delete(@PathVariable Long id) { expenseService.delete(id); }
}
