package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PurchaseOrderEmailRequest(
        @NotBlank @Email String to,
        @Email String cc,
        @Email String bcc,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 5000) String message,
        boolean attachPdf
) {}
