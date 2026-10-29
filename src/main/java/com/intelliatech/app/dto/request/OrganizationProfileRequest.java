package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationProfileRequest(
        @NotBlank @Size(max = 180) String organizationName,
        @Size(max = 180) String legalName,
        @Pattern(regexp = "(?i)^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "GSTIN format is invalid") String gstin,
        @Pattern(regexp = "(?i)^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message = "PAN format is invalid") String pan,
        @NotBlank @Pattern(regexp = "(?i)^[A-Z]{2}$") String countryCode,
        @Email @Size(max = 180) String email,
        @Size(max = 32) String phone,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 20) String postalCode,
        @Pattern(regexp = "^[A-Z]{3}$") String baseCurrency,
        String tdsBaseType
) {}
