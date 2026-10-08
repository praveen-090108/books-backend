package com.intelliatech.app.dto.response;

import java.time.LocalDateTime;

public record IrpAuthenticationToken(
        String clientId,
        String username,
        String authToken,
        byte[] sessionEncryptionKey,
        LocalDateTime expiresAt
) {
    public boolean expiresWithinMinutes(long minutes) {
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now().plusMinutes(minutes));
    }
}
