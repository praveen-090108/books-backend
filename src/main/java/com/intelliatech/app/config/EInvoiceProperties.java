package com.intelliatech.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.einvoice")
public record EInvoiceProperties(
        String publicKeyLocation
) {}
