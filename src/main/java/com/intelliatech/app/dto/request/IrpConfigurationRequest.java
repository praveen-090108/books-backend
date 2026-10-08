package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.*;

public record IrpConfigurationRequest(
        @NotBlank @Size(max=500) String apiBaseUrl,
        @Size(max=500) String clientId,
        @Size(max=500) String clientSecret,
        @Size(max=500) String apiUsername,
        @Size(max=500) String apiPassword,
        @NotBlank @Pattern(regexp="[0-9A-Z]{15}") String gstin,
        @Size(max=20) String apiVersion
) {}
