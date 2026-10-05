package com.intelliatech.app.controller;

import com.intelliatech.app.dto.response.FileUploadResponse;
import com.intelliatech.app.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/uploads")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse uploadBrandingLogo(@RequestPart("file") MultipartFile file) {
        return fileStorageService.uploadBrandingLogo(file);
    }

    @PostMapping(value = "/payments/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public FileUploadResponse uploadPaymentAttachment(@RequestPart("file") MultipartFile file) {
        return fileStorageService.uploadPaymentAttachment(file);
    }

    @PostMapping(value = "/purchase-orders/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public FileUploadResponse uploadPurchaseOrderAttachment(@RequestPart("file") MultipartFile file) {
        return fileStorageService.uploadPurchaseOrderAttachment(file);
    }

    @PostMapping(value = "/bills/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public FileUploadResponse uploadBillAttachment(@RequestPart("file") MultipartFile file) {
        return fileStorageService.uploadBillAttachment(file);
    }

    @PostMapping(value = "/leads/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public FileUploadResponse uploadLeadProfile(@RequestPart("file") MultipartFile file) {
        return fileStorageService.uploadLeadProfile(file);
    }

    @PostMapping(value = "/customers/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.can('sales', 'customers', 'CREATE') or @permissionGuard.can('sales', 'customers', 'EDIT')")
    public FileUploadResponse uploadCustomerAttachment(@RequestPart("file") MultipartFile file) {
        return fileStorageService.uploadCustomerAttachment(file);
    }
}
