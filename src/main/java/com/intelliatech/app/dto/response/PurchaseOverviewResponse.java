package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOverviewResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal totalPurchases,
        BigDecimal totalBills,
        long totalBillCount,
        BigDecimal totalExpenses,
        long totalExpenseCount,
        long totalVendors,
        long newVendors,
        BigDecimal pendingBillAmount,
        long pendingBillCount,
        long totalPurchaseOrders,
        List<MonthlyTotalResponse> currentTrend,
        List<MonthlyTotalResponse> previousTrend,
        List<OverviewPartyTotalResponse> topVendors,
        List<OverviewDocumentResponse> recentBills,
        List<OverviewCategoryTotalResponse> purchaseByCategory
) {
}
