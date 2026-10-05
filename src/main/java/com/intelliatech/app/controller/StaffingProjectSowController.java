package com.intelliatech.app.controller;

import com.intelliatech.app.service.StaffingProjectSowService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/staffing-projects/{projectId}/sows")
@RequiredArgsConstructor
public class StaffingProjectSowController {
    private final StaffingProjectSowService service;

    @GetMapping
    @PreAuthorize("@permissionGuard.can('projects','staffing','VIEW')")
    public List<StaffingProjectSowService.Sow> list(@PathVariable Long projectId) {
        return service.list(projectId);
    }

    @GetMapping("/current")
    @PreAuthorize("@permissionGuard.can('projects','staffing','VIEW')")
    public StaffingProjectSowService.Sow current(@PathVariable Long projectId) {
        return service.current(projectId);
    }

    @PostMapping(value="/initialize", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.can('projects','staffing','EDIT')")
    public StaffingProjectSowService.Sow initialize(@PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestPart("document") MultipartFile document,
            @RequestParam(required=false) String notes) {
        return service.initialize(projectId, startDate, endDate, document, notes);
    }

    @PutMapping(value="/current", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.can('projects','staffing','EDIT')")
    public StaffingProjectSowService.Sow updateCurrent(@PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestPart(value="document", required=false) MultipartFile document,
            @RequestParam(required=false) String notes) {
        return service.updateCurrent(projectId, startDate, endDate, document, notes);
    }

    @PostMapping(value="/renew", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.can('projects','staffing','EDIT')")
    public StaffingProjectSowService.Sow renew(@PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestPart("document") MultipartFile document,
            @RequestParam(required=false) String notes) {
        return service.renew(projectId, startDate, endDate, document, notes);
    }
}
