package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.*;

public record OrganizationRegistrationRequest(
        @NotBlank @Size(min=2,max=180) String organizationName,
        @Pattern(regexp="(?i)^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message="GSTIN format is invalid") String gstin,
        @Pattern(regexp="(?i)^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message="PAN format is invalid") String pan,
        @NotBlank @Pattern(regexp="(?i)^[A-Z]{2}$", message="Country must be a two-letter code") String countryCode,
        @NotBlank @Size(min=2,max=160) String adminFullName,
        @NotBlank @Pattern(regexp="^[A-Za-z][A-Za-z0-9._-]{3,39}$", message="Username must be 4-40 characters and use letters, numbers, dot, underscore or hyphen") String adminUsername,
        @NotBlank @Email @Size(max=160) String adminEmail,
        @NotBlank @Size(min=12,max=128) String password,
        @NotBlank String confirmPassword,
        @AssertTrue(message="You must accept the Terms of Service and Privacy Policy") boolean termsAccepted
) {}
