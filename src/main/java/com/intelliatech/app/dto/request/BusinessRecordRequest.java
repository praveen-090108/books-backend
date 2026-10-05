package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.intelliatech.app.entity.Department;
import jakarta.validation.constraints.Size;

public record BusinessRecordRequest(
        @NotBlank String recordNumber,
        @NotBlank String partyName,
        String partyEmail,
        String partyPhone,
        String partyCity,
        String category,
        @NotBlank String status,
        String secondaryStatus,
        @NotNull @PositiveOrZero BigDecimal amount,
        @PositiveOrZero BigDecimal balanceAmount,
        @NotNull LocalDate recordDate,
        LocalDate dueDate,
        LocalDate closedDate,
        String referenceNumber,
        String paymentMode,
        String ownerName,
        String notes,
        Department department,
        @Size(max = 150) String designation,
        Long reportingManagerId
) {
}
