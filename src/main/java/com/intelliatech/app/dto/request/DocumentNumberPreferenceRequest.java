package com.intelliatech.app.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentNumberPreferenceRequest(
        @NotNull Boolean autoGenerate,
        String prefix,
        String suffix,
        String separator,
        @NotBlank String numberFormat,
        @NotNull @Min(1) Long startingNumber,
        @NotNull @Min(1) Long nextNumber
) {
}
