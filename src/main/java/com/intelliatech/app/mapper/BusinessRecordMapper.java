package com.intelliatech.app.mapper;

import com.intelliatech.app.dto.request.BusinessRecordRequest;
import com.intelliatech.app.dto.response.BusinessRecordResponse;
import com.intelliatech.app.entity.BusinessRecord;
import org.springframework.stereotype.Component;

@Component
public class BusinessRecordMapper {

    public BusinessRecordResponse toResponse(BusinessRecord record) {
        return new BusinessRecordResponse(
                record.getId(),
                record.getModule(),
                record.getType(),
                record.getRecordNumber(),
                record.getPartyName(),
                record.getPartyEmail(),
                record.getPartyPhone(),
                record.getPartyCity(),
                record.getCategory(),
                record.getStatus(),
                record.getSecondaryStatus(),
                record.getAmount(),
                record.getBalanceAmount(),
                record.getRecordDate(),
                record.getDueDate(),
                record.getClosedDate(),
                record.getReferenceNumber(),
                record.getPaymentMode(),
                record.getOwnerName(),
                record.getNotes(),
                record.getCreatedBy(),
                record.getUpdatedBy(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                record.getDepartment(),
                record.getDesignation(),
                record.getReportingManager() == null ? null : record.getReportingManager().getId(),
                record.getReportingManager() == null ? null : record.getReportingManager().getPartyName()
        );
    }

    public BusinessRecord toEntity(String module, String type, BusinessRecordRequest request) {
        BusinessRecord record = new BusinessRecord();
        record.setModule(module);
        record.setType(type);
        copy(request, record);
        return record;
    }

    public void copy(BusinessRecordRequest request, BusinessRecord record) {
        record.setRecordNumber(request.recordNumber());
        record.setPartyName(request.partyName());
        record.setPartyEmail(request.partyEmail());
        record.setPartyPhone(request.partyPhone());
        record.setPartyCity(request.partyCity());
        record.setCategory(request.category());
        record.setStatus(request.status());
        record.setSecondaryStatus(request.secondaryStatus());
        record.setAmount(request.amount());
        record.setBalanceAmount(request.balanceAmount() == null ? java.math.BigDecimal.ZERO : request.balanceAmount());
        record.setRecordDate(request.recordDate());
        record.setDueDate(request.dueDate());
        record.setClosedDate(request.closedDate());
        record.setReferenceNumber(request.referenceNumber());
        record.setPaymentMode(request.paymentMode());
        record.setOwnerName(request.ownerName());
        record.setNotes(request.notes());
        record.setDepartment(request.department());
        record.setDesignation(request.designation() == null ? null : request.designation().trim());
    }
}
