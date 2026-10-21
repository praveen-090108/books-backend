package com.intelliatech.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.intelliatech.app.entity.Department;

public record BusinessRecordResponse(
        Long id,
        String module,
        String type,
        String recordNumber,
        String partyName,
        String partyEmail,
        String partyPhone,
        String partyCity,
        String category,
        String status,
        String employmentStatus,
        String secondaryStatus,
        BigDecimal amount,
        BigDecimal balanceAmount,
        LocalDate recordDate,
        LocalDate dueDate,
        LocalDate closedDate,
        LocalDate resignationDate,
        LocalDate noticeStartDate,
        LocalDate lastWorkingDay,
        String resignationReason,
        String employmentRemarks,
        String referenceNumber,
        String paymentMode,
        String ownerName,
        String notes,
        Long createdBy,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Department department,
        String designation,
        Long reportingManagerId,
        String reportingManagerName
) {
}
