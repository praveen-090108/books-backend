package com.intelliatech.app.dto.response;

import java.math.BigDecimal;

public record PurchaseOrderSummaryResponse(
        long totalOrders,
        long draftOrders,
        long issuedOrders,
        long partiallyReceivedOrders,
        long receivedOrders,
        long billedOrders,
        BigDecimal totalValue,
        BigDecimal outstandingValue,
        BigDecimal thisMonthValue
) {}
