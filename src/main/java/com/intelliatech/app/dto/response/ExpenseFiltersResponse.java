package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseFiltersResponse(
        List<Option> gstTreatments,
        List<StateOption> states,
        List<TaxOption> taxes,
        List<String> expenseAccounts,
        List<String> paymentModes,
        String organizationStateCode,
        String organizationStateName,
        String organizationCountry
) {
    public record Option(String value, String label, boolean requiresSourceOfSupply) {}
    public record StateOption(String code, String name, String label) {}
    public record TaxOption(Long id, String code, String name, String displayName, BigDecimal rate, String category) {}
}
