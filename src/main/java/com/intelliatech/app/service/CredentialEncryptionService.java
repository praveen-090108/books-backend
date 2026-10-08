package com.intelliatech.app.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CredentialEncryptionService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final String encodedKey;
    public CredentialEncryptionService(@Value("${irp.encryption.secret-key}") String encodedKey){this.encodedKey=encodedKey;}
    public String encrypt(String plainText){
        if(!StringUtils.hasText(plainText)) throw new IllegalArgumentException("Credential value is required");
        SecretKeySpec key=key();
        try{ byte[] nonce=new byte[12]; RANDOM.nextBytes(nonce); Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            byte[] ciphertext=c.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined=new byte[nonce.length+ciphertext.length];
            System.arraycopy(nonce,0,combined,0,nonce.length);
            System.arraycopy(ciphertext,0,combined,nonce.length,ciphertext.length);
            return "v2:"+Base64.getEncoder().encodeToString(combined);
        }catch(Exception e){throw new IllegalStateException("Unable to encrypt IRP credentials");}
    }
    public String decrypt(String encryptedValue){
        SecretKeySpec key=key();
        try{
            if(encryptedValue!=null&&encryptedValue.startsWith("v2:")){
                byte[] combined=Base64.getDecoder().decode(encryptedValue.substring(3));
                if(combined.length<29) throw new IllegalArgumentException("Ciphertext is too short");
                byte[] nonce=java.util.Arrays.copyOfRange(combined,0,12);
                byte[] ciphertext=java.util.Arrays.copyOfRange(combined,12,combined.length);
                Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));
                return new String(c.doFinal(ciphertext),StandardCharsets.UTF_8);
            }
            // Backward-compatible reader for credentials saved by the earlier v1 format.
            String[] p=encryptedValue.split(":",3); if(p.length!=3||!"v1".equals(p[0])) throw new IllegalArgumentException();
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(p[1])));
            return new String(c.doFinal(Base64.getDecoder().decode(p[2])),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException("Unable to decrypt IRP credentials. Verify the configured encryption key.");}
    }
    private SecretKeySpec key(){
        if(!StringUtils.hasText(encodedKey)) throw new IllegalStateException("irp.encryption.secret-key is not configured");
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(encodedKey);}catch(Exception e){throw new IllegalStateException("IRP credential encryption key must be Base64 encoded");}
        if(bytes.length!=32) throw new IllegalStateException("IRP credential encryption key must contain exactly 32 bytes");
        return new SecretKeySpec(bytes,"AES");
    }
}
