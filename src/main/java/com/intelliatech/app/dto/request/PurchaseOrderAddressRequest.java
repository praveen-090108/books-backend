package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.Size;

public record PurchaseOrderAddressRequest(
        @Size(max = 160) String attention,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 120) String city,
        @Size(max = 120) String state,
        @Size(max = 2) String stateCode,
        @Size(max = 120) String country,
        @Size(max = 20) String postalCode,
        @Size(max = 40) String phone,
        @Size(max = 180) String email,
        @Size(max = 20) String gstin
) {}
