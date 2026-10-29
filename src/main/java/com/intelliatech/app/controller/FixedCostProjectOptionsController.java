package com.intelliatech.app.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.intelliatech.app.security.TenantContext;

@RestController @RequestMapping("/api/projects/fixed-cost") @RequiredArgsConstructor
public class FixedCostProjectOptionsController {
    private final JdbcTemplate jdbc;
    private final TenantContext tenants;
    @GetMapping("/options") @PreAuthorize("@permissionGuard.can('projects','fixedCost','VIEW')")
    public Map<String,Object> options(){
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("employees",jdbc.queryForList("SELECT id,party_name name,category designation FROM business_records WHERE organization_id=? AND module='resources' AND type='resources' AND LOWER(status)='active' ORDER BY party_name",tenants.requireOrganizationId()));
        result.put("currencies",jdbc.queryForList("SELECT code,name,symbol,decimal_places decimalPlaces FROM currency_master WHERE active=TRUE ORDER BY display_order,code"));
        result.put("domains",jdbc.queryForList("SELECT id,name FROM domain_industry_master WHERE organization_id=? AND active=TRUE ORDER BY display_order,name",tenants.requireOrganizationId()));
        return result;
    }
}
