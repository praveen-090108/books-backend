package com.intelliatech.app.config;

import com.intelliatech.app.security.TenantContextHolder;
import java.util.Map;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantHibernateConfig {
    /**
     * Hibernate discriminator tenancy for the shared core entities. Zero is a
     * fail-closed identifier used when no organization request is authenticated.
     */
    @Bean
    CurrentTenantIdentifierResolver<Long> tenantIdentifierResolver() {
        return new CurrentTenantIdentifierResolver<>() {
            @Override
            public Long resolveCurrentTenantIdentifier() {
                Long organizationId = TenantContextHolder.getOrganizationIdOrNull();
                return organizationId == null ? 0L : organizationId;
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return true;
            }
        };
    }

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierCustomizer(
            CurrentTenantIdentifierResolver<Long> resolver) {
        return (Map<String, Object> properties) ->
                properties.put("hibernate.tenant_identifier_resolver", resolver);
    }
}
