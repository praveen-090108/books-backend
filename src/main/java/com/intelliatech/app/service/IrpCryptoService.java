package com.intelliatech.app.service;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class IrpCryptoService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public byte[] generateAppKey() {
        byte[] appKey = new byte[32];
        SECURE_RANDOM.nextBytes(appKey);
        return appKey;
    }

    public String encryptAuthenticationPayload(String json, PublicKey publicKey) {
        try {
            String base64Json = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(base64Json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt the IRP authentication request");
        }
    }

    public byte[] decryptSessionEncryptionKey(String encryptedSek, byte[] appKey) {
        return aesDecrypt(Base64.getDecoder().decode(encryptedSek), appKey);
    }

    public String encryptPayload(String json, byte[] sessionEncryptionKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionEncryptionKey, "AES"));
            return Base64.getEncoder().encodeToString(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt the IRP request payload");
        }
    }

    public String decryptPayload(String encryptedPayload, byte[] sessionEncryptionKey) {
        byte[] decrypted = aesDecrypt(Base64.getDecoder().decode(encryptedPayload), sessionEncryptionKey);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private byte[] aesDecrypt(byte[] encrypted, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt the IRP response payload");
        }
    }
}
