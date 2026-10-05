package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VendorStatementResponse(
        CompanyDetails company,
        VendorResponse vendor,
        LocalDate dateFrom,
        LocalDate dateTo,
        StatementSummary summary,
        List<StatementLine> transactions
) {
    public record CompanyDetails(
            String name,
            String address,
            String state,
            String pincode,
            String country,
            String gstin,
            String phone,
            String email,
            String website,
            String logoUrl
    ) {
    }

    public record StatementSummary(
            BigDecimal openingBalance,
            BigDecimal totalPurchases,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal totalPayments,
            BigDecimal totalVendorCredits,
            BigDecimal closingBalance
    ) {
    }

    public record StatementLine(
            LocalDate date,
            String transactionType,
            String transactionNumber,
            String description,
            String referenceNumber,
            LocalDate dueDate,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance,
            String status,
            String viewPath
    ) {
    }
}
