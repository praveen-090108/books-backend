package com.intelliatech.app.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * Request-bound tenant identity. The authentication filter owns the lifecycle:
 * set only after validating the JWT membership and always clear in a finally block.
 */
public final class TenantContextHolder {
    private static final ThreadLocal<Long> ORGANIZATION = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setOrganizationId(Long organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization id is required.");
        }
        ORGANIZATION.set(organizationId);
    }

    public static Long requireOrganizationId() {
        Long organizationId = ORGANIZATION.get();
        if (organizationId == null) {
            throw new AccessDeniedException("An active organization context is required.");
        }
        return organizationId;
    }

    public static Long getOrganizationIdOrNull() {
        return ORGANIZATION.get();
    }

    public static void clear() {
        ORGANIZATION.remove();
    }
}
