package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseOrderFiltersResponse(
        List<VendorOption> vendors,
        List<ItemOption> items,
        List<TaxOption> taxes,
        List<StateOption> states,
        List<String> gstTreatments,
        List<String> statuses,
        String organizationStateCode,
        String organizationStateName,
        String organizationCountry
) {
    public record VendorOption(
            Long id, String vendorNumber, String name, String displayName, String gstin,
            String gstTreatment, String sourceOfSupply, String currency, String paymentTerms,
            String contactName, String email, String phone, PurchaseOrderAddressResponse address
    ) {}
    public record ItemOption(
            Long id, String itemNumber, String name, String sku, String description, String itemType,
            String hsnCode, String sacCode, String unit, BigDecimal purchaseRate, Long taxId,
            BigDecimal taxRate, String taxName, String accountName, String status
    ) {}
    public record TaxOption(Long id, String code, String name, BigDecimal rate, String category) {}
    public record StateOption(String code, String name, String territoryType) {}
}
