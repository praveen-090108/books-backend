package com.intelliatech.app.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

public record IrpGenerateResult(
        boolean success,
        int httpStatus,
        JsonNode rawResponse,
        JsonNode decryptedData,
        String errorCode,
        String errorMessage
) {
}
