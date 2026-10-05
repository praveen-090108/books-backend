package com.intelliatech.app.dto.response;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        String phone,
        Instant createdAt
) {
}
