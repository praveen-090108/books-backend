package com.intelliatech.app.dto.request;

import com.intelliatech.app.entity.ExpenseAmountType;
import com.intelliatech.app.entity.GstTreatment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BillRequest(
        @NotBlank(message = "Bill number is required") @Size(max = 64) String billNumber,
        @NotNull(message = "Vendor is required") Long vendorId,
        @NotNull(message = "Bill date is required") LocalDate billDate,
        @NotNull(message = "Due date is required") LocalDate dueDate,
        @Size(max = 100) String referenceNumber,
        Long purchaseOrderId,
        @Size(max = 64) String paymentTerms,
        @NotBlank(message = "Place of Supply is required") @Pattern(regexp = "^[0-9]{2}$") String placeOfSupplyCode,
        @NotBlank(message = "Source of Supply is required") @Pattern(regexp = "^[0-9]{2}$") String sourceOfSupplyCode,
        @NotBlank(message = "Destination of Supply is required") @Pattern(regexp = "^[0-9]{2}$") String destinationOfSupplyCode,
        @NotNull(message = "GST treatment is required") GstTreatment gstTreatment,
        @NotBlank(message = "Currency is required") @Size(max = 16) String currencyCode,
        @NotNull @DecimalMin(value = "0.000001", message = "Exchange rate must be greater than zero") BigDecimal exchangeRate,
        @Size(max = 255) String subject,
        @NotNull ExpenseAmountType amountType,
        @DecimalMin(value = "0.00") BigDecimal shippingCharge,
        BigDecimal adjustmentAmount,
        @Size(max = 5000) String notes,
        @Size(max = 5000) String termsAndConditions,
        @Size(max = 255) String attachmentName,
        @Size(max = 1000) String attachmentUrl,
        @NotEmpty(message = "At least one item is required") List<@Valid BillItemRequest> items
) {}

