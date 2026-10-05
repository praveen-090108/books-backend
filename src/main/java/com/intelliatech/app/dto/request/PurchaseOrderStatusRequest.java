package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.Size;

public record PurchaseOrderStatusRequest(
        @Size(max = 1000, message = "Reason cannot exceed 1000 characters") String reason
) {}
