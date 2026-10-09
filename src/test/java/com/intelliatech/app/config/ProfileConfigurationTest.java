package com.intelliatech.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class ProfileConfigurationTest {
    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void sandboxIsDefaultAndCommonSettingsAreCentralized() throws Exception {
        var common = load("application.yml");
        var sandbox = load("application-sandbox.yml");

        assertThat(value(common, "spring.profiles.default")).isEqualTo("sandbox");
        assertThat(value(common, "server.port")).isEqualTo(8080);
        assertThat(value(sandbox, "app.environment")).isEqualTo("sandbox");
        assertThat(value(sandbox, "spring.datasource.url").toString()).contains("/intelliatech_books?");
    }

    @Test
    void productionUsesValidateAndDisablesUnsafeDevelopmentFeatures() throws Exception {
        var production = load("application-prod.yml");

        assertThat(value(production, "app.environment")).isEqualTo("production");
        assertThat(value(production, "spring.jpa.show-sql")).isEqualTo(false);
        assertThat(value(production, "app.bootstrap.default-admin-enabled")).isEqualTo(false);
        assertThat(value(production, "springdoc.swagger-ui.enabled")).isEqualTo(false);
    }

    @Test
    void productionValidationRejectsMissingCriticalConfiguration() {
        var environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.datasource.url", "jdbc:mysql://production-db-host.invalid:3306/intelliatech_books_prod");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ProductionEnvironmentValidator()
                .postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production configuration is incomplete")
                .hasMessageContaining("app.security.jwt-secret");
    }

    @Test
    void sandboxValidationRejectsNonLocalDatabaseOverride() {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://production.example:3306/intelliatech_books_prod");
        environment.setActiveProfiles("sandbox");

        assertThatThrownBy(() -> new ProductionEnvironmentValidator()
                .postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sandbox profile");
    }

    private List<PropertySource<?>> load(String name) throws Exception {
        return loader.load(name, new ClassPathResource(name));
    }

    private Object value(List<PropertySource<?>> sources, String key) {
        return sources.stream().map(source -> source.getProperty(key)).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }
}
