package com.intelliatech.app.controller;

import com.intelliatech.app.service.FixedCostProjectProfitabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/fixed-cost/{projectId}/profitability")
@RequiredArgsConstructor
public class FixedCostProjectProfitabilityController {
    private final FixedCostProjectProfitabilityService service;

    @GetMapping
    @PreAuthorize("@permissionGuard.can('projects','fixedCost','VIEW')")
    public FixedCostProjectProfitabilityService.Result calculate(@PathVariable Long projectId) {
        return service.calculate(projectId);
    }

    @GetMapping("/{yearMonth}/resources")
    @PreAuthorize("@permissionGuard.can('projects','fixedCost','VIEW')")
    public FixedCostProjectProfitabilityService.MonthResult resources(@PathVariable Long projectId,
            @PathVariable String yearMonth) {
        return service.month(projectId, yearMonth);
    }
}
