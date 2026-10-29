package com.intelliatech.app.dto.response;

public record OrganizationProfileResponse(
        Long organizationId,
        String organizationName,
        String legalName,
        String gstin,
        String pan,
        String countryCode,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String baseCurrency,
        String tdsBaseType
) {}
