package com.intelliatech.app.dto.request;

import com.intelliatech.app.entity.ExpenseAmountType;
import com.intelliatech.app.entity.ExpenseStatus;
import com.intelliatech.app.entity.ExpenseType;
import com.intelliatech.app.entity.GstTreatment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull(message = "Expense date is required") LocalDate expenseDate,
        @NotBlank(message = "Expense account is required") @Size(max = 120) String expenseAccount,
        Long expenseAccountId,
        @NotBlank(message = "Expense title is required") @Size(max = 180) String expenseTitle,
        @NotNull(message = "Expense type is required") ExpenseType expenseType,
        Long vendorId,
        @Pattern(regexp = "^$|^[A-Za-z0-9/\\- ]{1,100}$", message = "Invoice number may contain letters, numbers, slash, dash and spaces") String invoiceNumber,
        @Size(max = 16) String hsnCode,
        @Size(max = 16) String sacCode,
        @NotNull(message = "GST treatment is required") GstTreatment gstTreatment,
        @Pattern(regexp = "^$|^[0-9]{2}$") String sourceOfSupplyCode,
        @NotBlank(message = "Destination of Supply is required") @Pattern(regexp = "^[0-9]{2}$") String destinationOfSupplyCode,
        Long taxId,
        @NotNull(message = "Amount type is required") ExpenseAmountType amountType,
        @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be greater than zero") BigDecimal amount,
        @DecimalMin(value = "0.00", message = "TDS Deducted cannot be negative") BigDecimal tdsDeducted,
        @NotBlank(message = "Currency is required") @Size(max = 64) String currency,
        @Size(max = 100) String referenceNumber,
        @Size(max = 1000) String description,
        @Size(max = 1000) String notes,
        @Size(max = 64) String paymentMode,
        @Size(max = 120) String paidThrough,
        Long bankAccountId,
        @Size(max = 160) String projectName,
        @NotNull(message = "Status is required") ExpenseStatus status,
        @Size(max = 255) String attachmentName,
        @Size(max = 1000) String attachmentUrl
) {}
