package com.intelliatech.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EInvoiceProperties.class)
public class EInvoiceConfig {

    @Bean("irpRestClient")
    RestClient irpRestClient(RestClient.Builder builder) {
        // IRP 5's gateway is most reliable over HTTP/1.1. The JDK request
        // factory negotiates HTTP/2 when available and the gateway can return
        // a generic status-0 authentication response for that transport.
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        return builder.requestFactory(requestFactory).build();
    }
}
