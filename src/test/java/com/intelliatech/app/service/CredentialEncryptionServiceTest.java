package com.intelliatech.app.service;

import static org.assertj.core.api.Assertions.*;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class CredentialEncryptionServiceTest {
 private static final String TEST_KEY="AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
 @Test void loadsEncryptionKeyFromEnvironmentPlaceholder() throws Exception {Properties properties=new Properties();try(var input=getClass().getResourceAsStream("/application.properties")){properties.load(input);}assertThat(properties.getProperty("irp.encryption.secret-key")).isEqualTo("${IRP_CREDENTIAL_ENCRYPTION_KEY}");assertThat(Base64.getDecoder().decode(TEST_KEY)).hasSize(32);}
 @Test void encryptsWithUniqueNonceAndDecrypts(){var service=new CredentialEncryptionService(TEST_KEY);String first=service.encrypt("secret");String second=service.encrypt("secret");assertThat(first).startsWith("v2:").isNotEqualTo(second);assertThat(service.decrypt(first)).isEqualTo("secret");}
 @Test void encryptsAndDecryptsEveryIrpCredential(){var service=new CredentialEncryptionService(TEST_KEY);for(String value:new String[]{"client-id","client-secret","api-user","api-password"})assertThat(service.decrypt(service.encrypt(value))).isEqualTo(value);}
 @Test void failsSecurelyWithWrongKey(){byte[] a=new byte[32],b=new byte[32];b[0]=1;var writer=new CredentialEncryptionService(Base64.getEncoder().encodeToString(a));var reader=new CredentialEncryptionService(Base64.getEncoder().encodeToString(b));assertThatThrownBy(()->reader.decrypt(writer.encrypt("secret"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable to decrypt");}
 @Test void rejectsModifiedCiphertext(){var service=new CredentialEncryptionService(TEST_KEY);String encrypted=service.encrypt("secret");char replacement=encrypted.endsWith("A")?'B':'A';String tampered=encrypted.substring(0,encrypted.length()-1)+replacement;assertThatThrownBy(()->service.decrypt(tampered)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable to decrypt");}
 @Test void rejectsMissingMasterKey(){var service=new CredentialEncryptionService("");assertThatThrownBy(()->service.encrypt("secret")).isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");}
}
