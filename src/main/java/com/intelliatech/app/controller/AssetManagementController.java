package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.AssetManagementRequests.*;
import com.intelliatech.app.service.AssetManagementService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asset-management")
@RequiredArgsConstructor
public class AssetManagementController {
    private final AssetManagementService service;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.dashboard(from, to);
    }

    @GetMapping("/categories/options")
    public List<Map<String, Object>> categoryOptions() { return service.categoryOptions(); }

    @GetMapping("/categories")
    public Page<Map<String, Object>> categories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "categoryName") Pageable pageable) {
        return service.categories(search, status, pageable);
    }

    @GetMapping("/categories/{id}")
    public Map<String, Object> category(@PathVariable Long id) { return service.category(id); }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> createCategory(@Valid @RequestBody CategoryRequest request) {
        return service.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return service.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public void deleteCategory(@PathVariable Long id) { service.deleteCategory(id); }

    @GetMapping("/assets")
    public Page<Map<String, Object>> assets(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Long assignedResourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseTo,
            @RequestParam(required = false) String ownershipType,
            @RequestParam(required = false) String assetOwner,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return service.assets(search, categoryId, status, condition, location, assignedResourceId,
                purchaseFrom, purchaseTo, ownershipType, assetOwner, pageable);
    }

    @GetMapping("/assets/eligible-for-assignment")
    public List<Map<String, Object>> eligibleAssets() { return service.eligibleAssets(); }

    @GetMapping("/assets/{id}")
    public Map<String, Object> asset(@PathVariable Long id) { return service.asset(id); }

    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> createAsset(@Valid @RequestBody AssetRequest request) {
        return service.createAsset(request);
    }

    @PutMapping("/assets/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> updateAsset(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return service.updateAsset(id, request);
    }

    @DeleteMapping("/assets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public void deleteAsset(@PathVariable Long id) { service.deleteAsset(id); }

    @GetMapping("/assignments")
    public Page<Map<String, Object>> assignments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return service.assignments(search, status, pageable);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> createAssignment(@Valid @RequestBody AssignmentRequest request) {
        return service.createAssignment(request);
    }

    @PostMapping("/assignments/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> returnAssignment(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
            @RequestParam(required = false) String notes) {
        return service.returnAssignment(id, returnDate, notes);
    }

    @GetMapping("/maintenance")
    public Page<Map<String, Object>> maintenance(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 10, sort = "scheduledDate") Pageable pageable) {
        return service.maintenance(search, status, type, priority, technicianId, assetId, from, to, pageable);
    }

    @GetMapping("/maintenance/{id}")
    public Map<String, Object> maintenance(@PathVariable Long id) { return service.maintenance(id); }

    @PostMapping("/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> createMaintenance(@Valid @RequestBody MaintenanceRequest request) {
        return service.createMaintenance(request);
    }

    @PutMapping("/maintenance/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> updateMaintenance(@PathVariable Long id, @Valid @RequestBody MaintenanceRequest request) {
        return service.updateMaintenance(id, request);
    }

    @PostMapping("/maintenance/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> updateMaintenanceStatus(@PathVariable Long id,
                                                        @Valid @RequestBody MaintenanceStatusRequest request) {
        return service.updateMaintenanceStatus(id, request);
    }

    @DeleteMapping("/maintenance/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public void deleteMaintenance(@PathVariable Long id) { service.deleteMaintenance(id); }

    @GetMapping("/depreciation")
    public Page<Map<String, Object>> depreciation(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String financialYear,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return service.depreciation(search, financialYear, method, categoryId, status, pageable);
    }

    @GetMapping("/depreciation/{id}")
    public Map<String, Object> depreciation(@PathVariable Long id) { return service.depreciation(id); }

    @PostMapping("/depreciation")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> createDepreciation(@Valid @RequestBody DepreciationRequest request) {
        return service.createDepreciation(request);
    }

    @PutMapping("/depreciation/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> updateDepreciation(@PathVariable Long id, @Valid @RequestBody DepreciationRequest request) {
        return service.updateDepreciation(id, request);
    }

    @PostMapping("/depreciation/{id}/run")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> runDepreciation(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate throughDate) {
        return service.runDepreciation(id, throughDate);
    }

    @GetMapping("/disposals")
    public Page<Map<String, Object>> disposals(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return service.disposals(search, from, to, method, categoryId, status, pageable);
    }

    @GetMapping("/disposals/{id}")
    public Map<String, Object> disposal(@PathVariable Long id) { return service.disposal(id); }

    @PostMapping("/disposals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> createDisposal(@Valid @RequestBody DisposalRequest request) {
        return service.createDisposal(request);
    }

    @PutMapping("/disposals/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> updateDisposal(@PathVariable Long id, @Valid @RequestBody DisposalRequest request) {
        return service.updateDisposal(id, request);
    }

    @PostMapping("/disposals/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public Map<String, Object> updateDisposalStatus(@PathVariable Long id,
                                                    @Valid @RequestBody DisposalStatusRequest request) {
        return service.updateDisposalStatus(id, request);
    }

    @DeleteMapping("/disposals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public void deleteDisposal(@PathVariable Long id) { service.deleteDisposal(id); }
}
