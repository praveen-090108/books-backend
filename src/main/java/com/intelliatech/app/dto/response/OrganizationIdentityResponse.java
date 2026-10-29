package com.intelliatech.app.dto.response;

public record OrganizationIdentityResponse(
        Long organizationId,
        String organizationName,
        String logoUrl
) {}
