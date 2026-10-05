package com.intelliatech.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.local")
public record LocalStorageProperties(
        boolean enabled,
        String directory,
        String publicBaseUrl
) {
}
