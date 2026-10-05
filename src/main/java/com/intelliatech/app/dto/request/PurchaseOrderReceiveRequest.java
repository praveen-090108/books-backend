package com.intelliatech.app.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderReceiveRequest(
        @NotNull LocalDate receivedDate,
        @Size(max = 160) String warehouse,
        @Size(max = 1000) String notes,
        @NotEmpty List<@Valid ReceivedItem> items
) {
    public record ReceivedItem(
            @NotNull Long purchaseOrderItemId,
            @NotNull @DecimalMin(value = "0.0000") BigDecimal receivedQuantity
    ) {}
}
