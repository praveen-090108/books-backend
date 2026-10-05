package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOverviewResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal totalSales,
        long totalInvoices,
        BigDecimal averageInvoiceValue,
        long paidInvoices,
        BigDecimal paidAmount,
        BigDecimal outstandingReceivables,
        long overdueInvoices,
        BigDecimal overdueAmount,
        long totalCustomers,
        List<MonthlyTotalResponse> currentTrend,
        List<MonthlyTotalResponse> previousTrend,
        List<OverviewPartyTotalResponse> topCustomers,
        List<OverviewDocumentResponse> recentInvoices,
        List<OverviewCategoryTotalResponse> salesByChannel,
        List<OverviewCategoryTotalResponse> agingSummary
) {
}
