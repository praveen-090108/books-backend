package com.intelliatech.app.dto.response;

import com.intelliatech.app.entity.PurchaseOrderDiscountType;
import java.math.BigDecimal;

public record PurchaseOrderItemResponse(
        Long id,
        Long itemId,
        String itemName,
        String itemSku,
        String itemType,
        String description,
        String accountName,
        String hsnCode,
        String sacCode,
        BigDecimal quantity,
        BigDecimal receivedQuantity,
        BigDecimal billedQuantity,
        String unit,
        BigDecimal rate,
        PurchaseOrderDiscountType discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        Long taxId,
        String taxName,
        BigDecimal taxRate,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal cessAmount,
        BigDecimal lineTotal,
        String warehouseName,
        String projectName
) {}
