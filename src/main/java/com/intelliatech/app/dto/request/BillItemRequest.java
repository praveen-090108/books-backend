package com.intelliatech.app.dto.request;

import com.intelliatech.app.entity.PurchaseOrderDiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BillItemRequest(
        @NotNull(message = "Item is required") Long itemId,
        @Size(max = 1000) String description,
        @Size(max = 120) String accountName,
        @NotNull(message = "Quantity is required") @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero") BigDecimal quantity,
        @Size(max = 40) String unit,
        @NotNull(message = "Rate is required") @DecimalMin(value = "0.00", message = "Rate cannot be negative") BigDecimal rate,
        PurchaseOrderDiscountType discountType,
        @DecimalMin(value = "0.00", message = "Discount cannot be negative") BigDecimal discountValue,
        Long taxId,
        @Size(max = 24) String hsnCode,
        @Size(max = 24) String sacCode
) {}

