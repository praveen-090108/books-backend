package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.OrganizationProfileRequest;
import com.intelliatech.app.dto.response.OrganizationProfileResponse;
import com.intelliatech.app.service.OrganizationProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/organization-profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OrganizationProfileController {
    private final OrganizationProfileService service;

    @GetMapping
    public OrganizationProfileResponse get() { return service.get(); }

    @PutMapping
    public OrganizationProfileResponse update(@Valid @RequestBody OrganizationProfileRequest request) {
        return service.update(request);
    }
}
