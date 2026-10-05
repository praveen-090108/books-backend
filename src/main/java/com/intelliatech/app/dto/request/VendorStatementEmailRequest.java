package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VendorStatementEmailRequest(
        @NotBlank @Email String to,
        @Email @Size(max = 180) String cc,
        @Email @Size(max = 180) String bcc,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 4000) String message,
        boolean attachStatementPdf
) {
}
