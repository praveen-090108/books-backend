package com.intelliatech.app.service;

import com.intelliatech.app.dto.response.DashboardSummaryResponse;
import com.intelliatech.app.dto.response.PurchaseOverviewResponse;
import com.intelliatech.app.dto.response.SalesOverviewResponse;
import java.time.LocalDate;

public interface DashboardService {

    DashboardSummaryResponse getSummary(LocalDate dateFrom, LocalDate dateTo);

    SalesOverviewResponse getSalesOverview(LocalDate dateFrom, LocalDate dateTo);

    PurchaseOverviewResponse getPurchaseOverview(LocalDate dateFrom, LocalDate dateTo);
}
