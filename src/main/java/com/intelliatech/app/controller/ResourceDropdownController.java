package com.intelliatech.app.controller;

import com.intelliatech.app.dto.response.ResourceDropdownResponse;
import com.intelliatech.app.dto.response.ResourceLoginProfileResponse;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BusinessRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.intelliatech.app.entity.Department;
import org.springframework.jdbc.core.JdbcTemplate;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ResourceDropdownController {
    private final BusinessRecordRepository resources;
    private final JdbcTemplate jdbc;

    @GetMapping("/dropdown")
    public List<ResourceDropdownResponse> dropdown() {
        return resources.findAllByModuleAndTypeOrderByPartyNameAsc("resources", "resources").stream()
                .map(resource -> new ResourceDropdownResponse(resource.getId(), resource.getPartyName()))
                .toList();
    }

    @GetMapping("/sales-persons")
    public List<ResourceDropdownResponse> salesPersons() {
        return jdbc.query("SELECT r.id,r.party_name FROM app_users u JOIN business_records r ON r.id=u.resource_id WHERE LOWER(u.status)='active' AND LOWER(r.status)='active' AND LOWER(r.module)='resources' AND LOWER(r.type)='resources' AND UPPER(TRIM(u.role_name)) LIKE 'SALE%' ORDER BY r.party_name",
                (result,index)->new ResourceDropdownResponse(result.getLong("id"),result.getString("party_name")));
    }

    @GetMapping("/{id}/login-profile")
    public ResourceLoginProfileResponse loginProfile(@PathVariable Long id) {
        var resource = resources.findByModuleAndTypeAndId("resources", "resources", id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        return new ResourceLoginProfileResponse(resource.getId(), resource.getPartyName(), resource.getPartyEmail());
    }

    @GetMapping("/reporting-managers")
    @PreAuthorize("@permissionGuard.can('resources', 'resources', 'VIEW')")
    public List<ResourceDropdownResponse> reportingManagers(
            @RequestParam Department department,
            @RequestParam(required = false) Long excludeResourceId
    ) {
        return resources.findEligibleReportingManagers(department, excludeResourceId).stream()
                .map(resource -> new ResourceDropdownResponse(resource.getId(), resource.getPartyName()))
                .toList();
    }
}
