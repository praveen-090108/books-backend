package com.intelliatech.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.config.EInvoiceProperties;
import com.intelliatech.app.service.IrpCryptoService;
import com.intelliatech.app.service.IrpConfigurationService;
import com.intelliatech.app.dto.response.IrpCredentials;
import com.intelliatech.app.entity.IrpEnvironment;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class EyIrpAuthenticationLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "EINVOICE_LIVE_TEST", matches = "true")
    void authenticatesUsingApplicationJavaClient() {
        var properties = new EInvoiceProperties("classpath:einvoice/EY_IRP_Sandbox_auth_public_key_2027.pem");
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        var service = new EyIrpAuthenticationService(
                properties,
                configurationService(),
                RestClient.builder().requestFactory(requestFactory).build(),
                new ObjectMapper(),
                new IrpCryptoService(),
                new DefaultResourceLoader()
        );

        var token = service.refreshToken();

        assertThat(token.authToken()).isNotBlank();
        assertThat(token.sessionEncryptionKey().length).isIn(16, 24, 32);
    }

    private IrpConfigurationService configurationService() {
        IrpConfigurationService service=mock(IrpConfigurationService.class);
        String auth=System.getenv("EINVOICE_AUTH_URL");
        String base=auth.substring(0,auth.indexOf("/irpauthapi"));
        var credentials=new IrpCredentials(1L,1L,"EY_IRP_5", IrpEnvironment.SANDBOX,base,System.getenv("EINVOICE_CLIENT_ID"),System.getenv("EINVOICE_CLIENT_SECRET"),System.getenv("EINVOICE_USERNAME"),System.getenv("EINVOICE_PASSWORD"),System.getenv("EINVOICE_GSTIN"),"1.0",1L);
        when(service.getActiveCredentials(1L)).thenReturn(credentials);
        when(service.getDecryptedCredentials(1L,IrpEnvironment.SANDBOX)).thenReturn(credentials);
        return service;
    }
}
