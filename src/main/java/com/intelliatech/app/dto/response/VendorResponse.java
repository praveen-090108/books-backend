package com.intelliatech.app.dto.response;

import com.intelliatech.app.entity.VendorStatus;
import java.time.LocalDateTime;

public record VendorResponse(
        Long id,
        String vendorNumber,
        String vendorName,
        String displayName,
        String companyName,
        String vendorType,
        String sourceOfSupply,
        String currency,
        String paymentTerms,
        String taxTreatment,
        String gstin,
        String pan,
        VendorStatus status,
        String primaryContact,
        String email,
        String phone,
        String mobile,
        String website,
        String billingAddressLine1,
        String billingAddressLine2,
        String billingCity,
        String billingState,
        String billingPincode,
        String billingCountry,
        String shippingAddressLine1,
        String shippingAddressLine2,
        String shippingCity,
        String shippingState,
        String shippingPincode,
        String shippingCountry,
        VendorBankDetailsResponse bankDetails,
        VendorFinancialSummaryResponse financialSummary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
