package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.BusinessRecordRequest;
import com.intelliatech.app.dto.response.BusinessRecordResponse;
import com.intelliatech.app.dto.response.RecordSummaryResponse;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BusinessRecordService {

    Page<BusinessRecordResponse> findAll(
            String module,
            String type,
            String search,
            String status,
            String secondaryStatus,
            String category,
            String department,
            String partyName,
            LocalDate dateFrom,
            LocalDate dateTo,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String paymentState,
            Boolean overdue,
            Pageable pageable
    );

    BusinessRecordResponse findById(String module, String type, Long id);

    BusinessRecordResponse create(String module, String type, BusinessRecordRequest request);

    BusinessRecordResponse update(String module, String type, Long id, BusinessRecordRequest request);

    void delete(String module, String type, Long id);

    RecordSummaryResponse summary(
            String module,
            String type,
            String search,
            String status,
            String secondaryStatus,
            String category,
            String partyName,
            LocalDate dateFrom,
            LocalDate dateTo,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            String paymentState,
            Boolean overdue
    );
}
