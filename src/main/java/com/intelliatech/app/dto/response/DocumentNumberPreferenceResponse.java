package com.intelliatech.app.dto.response;

public record DocumentNumberPreferenceResponse(
        Long id,
        String documentType,
        Boolean autoGenerate,
        String prefix,
        String suffix,
        String separator,
        String numberFormat,
        Long startingNumber,
        Long nextNumber,
        String previewNumber
) {
}
