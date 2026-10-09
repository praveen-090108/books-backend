package com.intelliatech.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.storage.s3")
public record S3StorageProperties(
        boolean enabled,
        String bucket,
        String region,
        String accessKeyId,
        String secretAccessKey,
        String keyPrefix,
        String publicBaseUrl
) {
    public boolean active() {
        return enabled || StringUtils.hasText(bucket);
    }

    public boolean complete() {
        return active() && StringUtils.hasText(bucket) && StringUtils.hasText(region);
    }
}
