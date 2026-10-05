package com.intelliatech.app.dto.request;

import com.intelliatech.app.entity.VendorStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VendorRequest(
        @NotBlank(message = "Vendor name is required") @Size(max = 160) String vendorName,
        @NotBlank(message = "Display name is required") @Size(max = 160) String displayName,
        @Size(max = 160) String companyName,
        @Size(max = 64) String vendorType,
        @Size(max = 120) String sourceOfSupply,
        @NotBlank(message = "Currency is required") @Size(max = 64) String currency,
        @Size(max = 64) String paymentTerms,
        @Size(max = 80) String taxTreatment,
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "Enter a valid GSTIN") String gstin,
        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message = "Enter a valid PAN") String pan,
        @NotNull(message = "Status is required") VendorStatus status,
        @Size(max = 160) String primaryContact,
        @Email(message = "Enter a valid email address") @Size(max = 180) String email,
        @Pattern(regexp = "^$|^[+0-9()\\-\\s]{7,40}$", message = "Enter a valid phone number") String phone,
        @Pattern(regexp = "^$|^[+0-9()\\-\\s]{7,40}$", message = "Enter a valid mobile number") String mobile,
        @Pattern(regexp = "^$|^https?://.+$", message = "Website must start with http:// or https://") @Size(max = 255) String website,
        @Size(max = 255) String billingAddressLine1,
        @Size(max = 255) String billingAddressLine2,
        @Size(max = 120) String billingCity,
        @Size(max = 120) String billingState,
        @Size(max = 20) String billingPincode,
        @Size(max = 120) String billingCountry,
        @Size(max = 255) String shippingAddressLine1,
        @Size(max = 255) String shippingAddressLine2,
        @Size(max = 120) String shippingCity,
        @Size(max = 120) String shippingState,
        @Size(max = 20) String shippingPincode,
        @Size(max = 120) String shippingCountry,
        @Valid VendorBankDetailsRequest bankDetails
) {
}
