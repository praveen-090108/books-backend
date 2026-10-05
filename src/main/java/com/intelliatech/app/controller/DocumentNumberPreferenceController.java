package com.intelliatech.app.controller;

import com.intelliatech.app.dto.request.DocumentNumberPreferenceRequest;
import com.intelliatech.app.dto.response.DocumentNumberPreferenceResponse;
import com.intelliatech.app.service.DocumentNumberPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/document-number-preferences/{documentType}")
@RequiredArgsConstructor
public class DocumentNumberPreferenceController {

    private final DocumentNumberPreferenceService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public DocumentNumberPreferenceResponse get(@PathVariable String documentType) {
        return service.get(documentType);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentNumberPreferenceResponse save(@PathVariable String documentType, @Valid @RequestBody DocumentNumberPreferenceRequest request) {
        return service.save(documentType, request);
    }

    @PostMapping("/consume")
    public DocumentNumberPreferenceResponse consume(@PathVariable String documentType) {
        return service.consume(documentType);
    }
}
