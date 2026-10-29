package com.intelliatech.app.controller;

import com.intelliatech.app.dto.response.OrganizationIdentityResponse;
import com.intelliatech.app.service.OrganizationProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization")
@RequiredArgsConstructor
public class OrganizationIdentityController {
    private final OrganizationProfileService service;

    @GetMapping("/identity")
    public OrganizationIdentityResponse identity() {
        return service.identity();
    }
}
