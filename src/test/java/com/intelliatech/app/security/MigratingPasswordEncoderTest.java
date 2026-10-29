package com.intelliatech.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class MigratingPasswordEncoderTest {
    private final MigratingPasswordEncoder encoder = new MigratingPasswordEncoder();

    @Test
    void newPasswordsUseArgon2id() {
        String encoded = encoder.encode("StrongPassword!2026");
        assertThat(encoded).startsWith("$argon2id$");
        assertThat(encoder.matches("StrongPassword!2026", encoded)).isTrue();
        assertThat(encoder.matches("incorrect", encoded)).isFalse();
    }

    @Test
    void legacyBcryptPasswordsRemainValidAndAreMarkedForUpgrade() {
        String legacy = new BCryptPasswordEncoder().encode("LegacyPassword!2026");
        assertThat(encoder.matches("LegacyPassword!2026", legacy)).isTrue();
        assertThat(encoder.upgradeEncoding(legacy)).isTrue();
    }
}
