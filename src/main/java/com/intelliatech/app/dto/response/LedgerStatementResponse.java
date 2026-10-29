package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LedgerStatementResponse(
        String partyType, Party party, Organization organization, LocalDate fromDate, LocalDate toDate,
        String currency, BigDecimal openingBalance, BigDecimal totalDebit, BigDecimal totalCredit,
        BigDecimal closingBalance, List<Line> transactions
) {
    public record Party(Long id, String code, String name, String email, String phone, String address) {}
    public record Organization(String name, String address, String gstin, String email, String phone, String logoUrl) {}
    public record Line(String id, LocalDate date, String particulars, String transactionType, String referenceNumber,
                       BigDecimal debit, BigDecimal credit, BigDecimal runningBalance, String balanceSide,
                       String status, String viewPath) {}
}
