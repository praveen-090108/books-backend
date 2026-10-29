package com.intelliatech.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

class TenantContextHolderTest {
    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void bindsAndClearsOrganizationForCurrentThread() {
        TenantContextHolder.setOrganizationId(42L);
        assertThat(TenantContextHolder.requireOrganizationId()).isEqualTo(42L);
        TenantContextHolder.clear();
        assertThatThrownBy(TenantContextHolder::requireOrganizationId)
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void refusesMissingOrganizationContext() {
        assertThatThrownBy(TenantContextHolder::requireOrganizationId)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("organization context");
    }

    @Test
    void isolatesThreeOrganizationsAcrossConcurrentRequests() throws Exception {
        try (var executor = Executors.newFixedThreadPool(3)) {
            List<Callable<Long>> requests = List.of(
                    tenantRequest(101L), tenantRequest(202L), tenantRequest(303L));

            assertThat(executor.invokeAll(requests).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            })).containsExactlyInAnyOrder(101L, 202L, 303L);
        }
    }

    private Callable<Long> tenantRequest(Long organizationId) {
        return () -> {
            TenantContextHolder.setOrganizationId(organizationId);
            try {
                return TenantContextHolder.requireOrganizationId();
            } finally {
                TenantContextHolder.clear();
            }
        };
    }
}
