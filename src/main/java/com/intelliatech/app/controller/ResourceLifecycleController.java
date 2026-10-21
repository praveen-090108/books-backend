package com.intelliatech.app.controller;

import com.intelliatech.app.service.ResourceLifecycleService;
import java.util.List;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceLifecycleController {
    private final ResourceLifecycleService service;

    @GetMapping("/availability")
    @PreAuthorize("@permissionGuard.can('resources','resources','VIEW')")
    public ResourceLifecycleService.AvailabilitySummary availability() { return service.availability(); }

    @GetMapping("/availability/dashboard")
    @PreAuthorize("@permissionGuard.can('resources','resources','VIEW')")
    public ResourceLifecycleService.AvailabilityDashboard availabilityDashboard(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int releaseDays) {
        return service.availabilityDashboard(releaseDays);
    }

    @GetMapping("/availability/export")
    @PreAuthorize("@permissionGuard.can('resources','resources','DOWNLOAD')")
    public ResponseEntity<byte[]> exportAvailability(
            @org.springframework.web.bind.annotation.RequestParam(required = false) List<Long> resourceIds) throws Exception {
        var dashboard = service.availabilityDashboard(90);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Resource Availability");
            String[] headings = {"Resource", "Employee Code", "Designation", "Department", "Skills",
                    "Reporting Manager", "Employment Status", "Allocation %", "Available %",
                    "Customers", "Projects", "Expected Release", "Available From"};
            var header = sheet.createRow(0);
            for (int column = 0; column < headings.length; column++) header.createCell(column).setCellValue(headings[column]);
            int rowNumber = 1;
            for (var resource : dashboard.resources().stream()
                    .filter(item -> resourceIds == null || resourceIds.isEmpty() || resourceIds.contains(item.resourceId()))
                    .toList()) {
                var row = sheet.createRow(rowNumber++);
                Object[] values = {resource.resourceName(), resource.employeeCode(), resource.designation(),
                        resource.department(), resource.skills(), resource.reportingManager(), resource.employmentStatus(),
                        resource.allocatedPercentage(), resource.availablePercentage(), resource.customers(),
                        resource.projects(), resource.expectedRelease(), resource.availableFrom()};
                for (int column = 0; column < values.length; column++) row.createCell(column).setCellValue(values[column] == null ? "" : values[column].toString());
            }
            for (int column = 0; column < headings.length; column++) sheet.autoSizeColumn(column);
            workbook.write(output);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resource-availability.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(output.toByteArray());
        }
    }

    @GetMapping("/{id}/salary-history")
    @PreAuthorize("@permissionGuard.can('resources','resources','EDIT')")
    public List<ResourceLifecycleService.SalaryRevision> salaryHistory(@PathVariable Long id) { return service.salaryHistory(id); }

    @PostMapping("/{id}/salary-revisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionGuard.can('resources','resources','EDIT')")
    public ResourceLifecycleService.SalaryRevision reviseSalary(@PathVariable Long id,
            @RequestBody ResourceLifecycleService.SalaryRevisionRequest request) { return service.reviseSalary(id, request); }

    @GetMapping("/{id}/employment-history")
    @PreAuthorize("@permissionGuard.can('resources','resources','VIEW')")
    public List<ResourceLifecycleService.EmploymentEntry> employmentHistory(@PathVariable Long id) { return service.employmentHistory(id); }

    @PutMapping("/{id}/employment-status")
    @PreAuthorize("@permissionGuard.can('resources','resources','EDIT')")
    public ResourceLifecycleService.EmploymentEntry updateEmployment(@PathVariable Long id,
            @RequestBody ResourceLifecycleService.EmploymentRequest request) { return service.updateEmployment(id, request); }

    @GetMapping("/{id}/project-history")
    @PreAuthorize("@permissionGuard.can('resources','resources','VIEW')")
    public List<ResourceLifecycleService.AllocationEntry> projectHistory(@PathVariable Long id) { return service.projectHistory(id); }
}
