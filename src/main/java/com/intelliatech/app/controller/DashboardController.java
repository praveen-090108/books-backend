package com.intelliatech.app.controller;

import com.intelliatech.app.dto.response.DashboardSummaryResponse;
import com.intelliatech.app.dto.response.PurchaseOverviewResponse;
import com.intelliatech.app.dto.response.SalesOverviewResponse;
import com.intelliatech.app.service.DashboardService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DASHBOARD_OVERVIEW_VIEW')")
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return service.getSummary(dateFrom, dateTo);
    }

    @GetMapping("/sales-overview")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SALES_OVERVIEW_VIEW')")
    public SalesOverviewResponse salesOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return service.getSalesOverview(dateFrom, dateTo);
    }

    @GetMapping("/purchase-overview")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PURCHASE_OVERVIEW_VIEW')")
    public PurchaseOverviewResponse purchaseOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return service.getPurchaseOverview(dateFrom, dateTo);
    }
}
