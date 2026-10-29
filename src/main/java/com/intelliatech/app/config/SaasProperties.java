package com.intelliatech.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.saas")
public record SaasProperties(boolean registrationEnabled) {}
