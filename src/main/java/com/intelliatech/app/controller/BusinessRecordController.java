package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.BusinessRecordRequest;
import com.intelliatech.app.dto.response.BusinessRecordResponse;
import com.intelliatech.app.dto.response.RecordSummaryResponse;
import com.intelliatech.app.service.BusinessRecordService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/records/{module}/{type}")
@RequiredArgsConstructor
public class BusinessRecordController {

    private final BusinessRecordService service;

    @GetMapping
    @PreAuthorize("@permissionGuard.can(#module, #type, 'VIEW')")
    public Page<BusinessRecordResponse> findAll(
            @PathVariable String module,
            @PathVariable String type,
            String search,
            String status,
            String secondaryStatus,
            String category,
            String department,
            String partyName,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            String paymentState,
            Boolean overdue,
            Pageable pageable
    ) {
        return service.findAll(module, type, search, status, secondaryStatus, category, department, partyName, dateFrom, dateTo, dueDateFrom, dueDateTo, paymentState, overdue, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("@permissionGuard.can(#module, #type, 'VIEW')")
    public RecordSummaryResponse summary(
            @PathVariable String module,
            @PathVariable String type,
            String search,
            String status,
            String secondaryStatus,
            String category,
            String partyName,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            String paymentState,
            Boolean overdue
    ) {
        return service.summary(module, type, search, status, secondaryStatus, category, partyName, dateFrom, dateTo, dueDateFrom, dueDateTo, paymentState, overdue);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.can(#module, #type, 'VIEW')")
    public BusinessRecordResponse findById(@PathVariable String module, @PathVariable String type, @PathVariable Long id) {
        return service.findById(module, type, id);
    }

    @PostMapping
    @PreAuthorize("@permissionGuard.can(#module, #type, 'CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessRecordResponse create(@PathVariable String module, @PathVariable String type, @Valid @RequestBody BusinessRecordRequest request) {
        return service.create(module, type, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionGuard.can(#module, #type, 'EDIT')")
    public BusinessRecordResponse update(@PathVariable String module, @PathVariable String type, @PathVariable Long id, @Valid @RequestBody BusinessRecordRequest request) {
        return service.update(module, type, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionGuard.can(#module, #type, 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String module, @PathVariable String type, @PathVariable Long id) {
        service.delete(module, type, id);
    }
}
