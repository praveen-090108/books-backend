package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal totalReceivables,
        BigDecimal totalPayables,
        BigDecimal cashFlowNet,
        BigDecimal netProfit,
        BigDecimal totalSales,
        BigDecimal totalPurchases,
        long totalInvoices,
        long paidInvoices,
        long overdueInvoices,
        long totalCustomers,
        long totalVendors,
        long totalSalesRecords,
        long totalPurchaseRecords,
        List<MonthlyTotalResponse> monthlySales,
        List<MonthlyTotalResponse> monthlyPurchases,
        List<BusinessRecordResponse> recentTransactions
) {
}
