package com.intelliatech.app.dto.response;

import java.util.List;

public record BillFiltersResponse(
        List<PurchaseOrderFiltersResponse.VendorOption> vendors,
        List<PurchaseOrderFiltersResponse.ItemOption> items,
        List<PurchaseOrderFiltersResponse.TaxOption> taxes,
        List<PurchaseOrderFiltersResponse.StateOption> states,
        List<PurchaseOrderOption> purchaseOrders,
        List<String> gstTreatments,
        List<String> statuses,
        String organizationStateCode,
        String organizationStateName,
        String organizationCountry
) {
    public record PurchaseOrderOption(Long id, String number, Long vendorId, String vendorName, String status) {}
}

