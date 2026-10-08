package com.intelliatech.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class IrpCryptoServiceTest {

    private final IrpCryptoService service = new IrpCryptoService();

    @Test
    void generatesThirtyTwoByteAppKey() {
        byte[] appKey = service.generateAppKey();

        assertThat(appKey).hasSize(32);
        assertThat(Base64.getEncoder().encodeToString(appKey)).hasSize(44);
    }

    @Test
    void encryptsAndDecryptsPayloadWithSessionKey() {
        byte[] sessionKey = service.generateAppKey();
        String payload = "{\"Version\":\"1.1\",\"DocDtls\":{\"No\":\"INV-1\"}}";

        String encrypted = service.encryptPayload(payload, sessionKey);

        assertThat(encrypted).isNotEqualTo(payload);
        assertThat(service.decryptPayload(encrypted, sessionKey)).isEqualTo(payload);
    }

    @Test
    void authenticationEnvelopeUsesRsaPublicKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();

        String encrypted = service.encryptAuthenticationPayload(
                "{\"UserName\":\"testuser\",\"Password\":\"secret\"}",
                keyPair.getPublic()
        );

        assertThat(Base64.getDecoder().decode(encrypted)).hasSize(256);
        assertThat(encrypted.getBytes(StandardCharsets.UTF_8)).doesNotContain((byte) '{');
    }
}
