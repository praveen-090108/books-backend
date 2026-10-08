package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CancelIrnRequest(
        @NotBlank(message = "Cancellation reason is required")
        @Pattern(regexp = "[1-4]", message = "Cancellation reason must be 1, 2, 3 or 4")
        String reasonCode,
        @Size(max = 100, message = "Cancellation remarks cannot exceed 100 characters")
        String remarks
) {
}
