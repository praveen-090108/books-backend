package com.intelliatech.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String provider) {

    public boolean usesS3() {
        return provider == null || provider.isBlank() || "s3".equalsIgnoreCase(provider.trim());
    }
}
