package com.intelliatech.app.dto.request;

import com.intelliatech.app.entity.ExpenseAmountType;
import com.intelliatech.app.entity.GstTreatment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderRequest(
        @NotNull(message = "Purchase Order date is required") LocalDate purchaseOrderDate,
        LocalDate expectedDeliveryDate,
        @NotNull(message = "Vendor is required") Long vendorId,
        @Size(max = 100) String referenceNumber,
        @Size(max = 80) String shipmentPreference,
        @Size(max = 64) String paymentTerms,
        @NotBlank(message = "Currency is required") @Size(max = 16) String currencyCode,
        @NotNull @DecimalMin(value = "0.000001", message = "Exchange rate must be greater than zero") BigDecimal exchangeRate,
        @NotNull(message = "GST treatment is required") GstTreatment gstTreatment,
        @NotBlank(message = "Source of Supply is required") @Pattern(regexp = "^[0-9]{2}$") String sourceOfSupplyCode,
        @NotBlank(message = "Destination of Supply is required") @Pattern(regexp = "^[0-9]{2}$") String destinationOfSupplyCode,
        @Pattern(regexp = "^$|^[0-9]{2}$") String placeOfSupplyCode,
        @Size(max = 40) String deliveryAddressSource,
        @Valid PurchaseOrderAddressRequest vendorAddress,
        @Valid PurchaseOrderAddressRequest deliveryAddress,
        @Size(max = 160) String projectName,
        @Size(max = 160) String branchName,
        @Size(max = 160) String warehouseName,
        @Size(max = 160) String attention,
        @NotNull ExpenseAmountType amountType,
        @DecimalMin(value = "0.00") BigDecimal shippingCharge,
        BigDecimal adjustmentAmount,
        @Size(max = 5000) String notes,
        @Size(max = 5000) String termsAndConditions,
        @Size(max = 255) String attachmentName,
        @Size(max = 1000) String attachmentUrl,
        @NotEmpty(message = "At least one item is required") List<@Valid PurchaseOrderItemRequest> items
) {}
