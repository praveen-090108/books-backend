package com.intelliatech.app.dto.response;

public record FileUploadResponse(
        String key,
        String url,
        String fileName,
        String contentType,
        long size
) {
}
