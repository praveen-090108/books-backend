package com.intelliatech.app.controller;

import com.intelliatech.app.service.ResourceLifecycleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/resources")
@RequiredArgsConstructor
public class ProjectResourceLifecycleController {
    private final ResourceLifecycleService service;

    @GetMapping
    @PreAuthorize("@permissionGuard.can('projects','staffing','VIEW') or @permissionGuard.can('projects','fixedCost','VIEW')")
    public List<ResourceLifecycleService.AllocationEntry> allocations(@PathVariable Long projectId) { return service.projectAllocations(projectId); }

    @PostMapping("/{allocationId}/schedule-release")
    @PreAuthorize("@permissionGuard.can('projects','staffing','EDIT') or @permissionGuard.can('projects','fixedCost','EDIT')")
    public ResourceLifecycleService.AllocationEntry scheduleRelease(@PathVariable Long projectId,
            @PathVariable Long allocationId, @RequestBody ResourceLifecycleService.ReleaseRequest request) {
        return service.scheduleRelease(projectId, allocationId, request);
    }

    @PostMapping("/{allocationId}/close")
    @PreAuthorize("@permissionGuard.can('projects','staffing','EDIT') or @permissionGuard.can('projects','fixedCost','EDIT')")
    public ResourceLifecycleService.AllocationEntry close(@PathVariable Long projectId,
            @PathVariable Long allocationId, @RequestBody ResourceLifecycleService.CloseAllocationRequest request) {
        return service.closeAllocation(projectId, allocationId, request);
    }

    @GetMapping("/billing-summary")
    @PreAuthorize("@permissionGuard.can('projects','staffing','VIEW')")
    public ResourceLifecycleService.BillingSummary billing(@PathVariable Long projectId) { return service.billingSummary(projectId); }
}
