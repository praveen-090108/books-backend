package com.intelliatech.app.controller;

import com.intelliatech.app.service.ProjectInvoiceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project-invoices")
@RequiredArgsConstructor
public class ProjectInvoiceController {
    private final ProjectInvoiceService service;

    @GetMapping("/lookup")
    @PreAuthorize("@permissionGuard.can('sales','invoices','CREATE') or @permissionGuard.can('sales','invoices','EDIT')")
    public List<ProjectInvoiceService.ProjectOption> lookup(@RequestParam String projectType,
                                                             @RequestParam(required = false) String customer) {
        return service.lookup(projectType, customer);
    }

    @GetMapping("/projects/{projectType}/{projectId}")
    @PreAuthorize("@permissionGuard.can('projects',#projectType,'VIEW') and @permissionGuard.can('sales','invoices','VIEW')")
    public ProjectInvoiceService.ProjectInvoices invoices(@PathVariable String projectType, @PathVariable Long projectId) {
        return service.invoices(projectType, projectId);
    }
}
