package com.intelliatech.app.security;

import com.intelliatech.app.service.AppUserService;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the permission codes used by Role Management for generic record APIs.
 * Record ownership is enforced separately by BusinessRecordServiceImpl.
 */
@Component("permissionGuard")
@RequiredArgsConstructor
public class PermissionGuard {
    private final CurrentUserService currentUsers;
    private final AppUserService users;

    public boolean can(String module, String type, String action) {
        var current = currentUsers.getCurrentUser();
        if (currentUsers.isAdmin(current)) return true;
        Set<String> permissions = users.permissionsFor(current.getRoleName()).stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (permissions.contains("*")) return true;
        String moduleCode = code(module);
        if ("PURCHASES".equals(moduleCode)) moduleCode = "PURCHASE";
        if ("LEAD_MANAGEMENT".equals(moduleCode)) moduleCode = "LEAD";
        if ("ASSET_MANAGEMENT".equals(moduleCode)) moduleCode = "ASSET";
        String typeCode = code(type);
        return permissions.contains(moduleCode + "_" + typeCode + "_" + code(action));
    }

    private String code(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_|_$)", "");
    }
}
