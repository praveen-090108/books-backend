package com.intelliatech.app.dto.response;

public record PurchaseOrderAddressResponse(
        String attention,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String stateCode,
        String country,
        String postalCode,
        String phone,
        String email,
        String gstin
) {}
