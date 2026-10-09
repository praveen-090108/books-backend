package com.intelliatech.app.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

public class ProductionEnvironmentValidator implements EnvironmentPostProcessor, Ordered {
    private static final List<String> REQUIRED = List.of(
            "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "app.frontend-url", "app.security.jwt-secret", "app.storage.s3.bucket",
            "app.storage.s3.public-base-url", "app.einvoice.public-key-location",
            "irp.encryption.secret-key"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        boolean sandbox = environment.acceptsProfiles(Profiles.of("sandbox"));
        if (production && sandbox) {
            throw new IllegalStateException("Sandbox and Production profiles cannot be active at the same time.");
        }
        if (sandbox) {
            String sandboxUrl = property(environment, "spring.datasource.url");
            if (StringUtils.hasText(sandboxUrl) && !sandboxUrl.contains("127.0.0.1") && !sandboxUrl.contains("localhost")) {
                throw new IllegalStateException("Sandbox profile may only use the configured local Sandbox database.");
            }
            return;
        }
        if (!production) return;
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED) {
            String value = property(environment, key);
            if (!StringUtils.hasText(value) || value.contains("CHANGE_ME")) missing.add(key);
        }
        String url = property(environment, "spring.datasource.url");
        if (url.contains("production-db-host.invalid") || url.contains("127.0.0.1") || url.contains("localhost")) {
            missing.add("PROD_DB_HOST (must identify the approved production database)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Production configuration is incomplete. Provide: " + String.join(", ", missing));
        }
    }

    private String property(ConfigurableEnvironment environment, String key) {
        try {
            return environment.getProperty(key, "");
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return "";
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
