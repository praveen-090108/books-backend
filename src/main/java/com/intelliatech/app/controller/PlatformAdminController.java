package com.intelliatech.app.controller;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import com.intelliatech.app.service.OrganizationRegistrationService;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('PLATFORM_SUPER_ADMIN')")
@RequiredArgsConstructor
public class PlatformAdminController {
    private final JdbcTemplate jdbc;
    private final OrganizationRegistrationService registrations;

    public record MessageResponse(String message) {}

    @PostMapping("/organizations/{publicId}/resend-verification")
    public MessageResponse resendVerification(@PathVariable String publicId) {
        registrations.resendForOrganization(publicId);
        return new MessageResponse("A new verification email has been requested for the pending organization administrator.");
    }

    @GetMapping("/dashboard")
    public Map<String,Object> dashboard() {
        return Map.of(
                "totalOrganizations", count("SELECT COUNT(*) FROM organizations"),
                "activeOrganizations", count("SELECT COUNT(*) FROM organizations WHERE status='ACTIVE'"),
                "pendingOrganizations", count("SELECT COUNT(*) FROM organizations WHERE status='PENDING_VERIFICATION'"),
                "suspendedOrganizations", count("SELECT COUNT(*) FROM organizations WHERE status='SUSPENDED'"),
                "totalUsers", count("SELECT COUNT(*) FROM app_users WHERE platform_admin=FALSE"),
                "newOrganizationsThisMonth", count("SELECT COUNT(*) FROM organizations WHERE created_at >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')"),
                "recentOrganizations", organizationRows("", "", 0, 8)
        );
    }

    @GetMapping("/organizations")
    public Map<String,Object> organizations(@RequestParam(defaultValue="") String search,
                                            @RequestParam(defaultValue="") String status,
                                            @RequestParam(defaultValue="0") int page,
                                            @RequestParam(defaultValue="20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100), safePage = Math.max(page, 0);
        String pattern = "%" + search.trim().toLowerCase() + "%";
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        long total = jdbc.queryForObject("""
            SELECT COUNT(*) FROM organizations o
            LEFT JOIN organization_memberships m ON m.organization_id=o.id
            LEFT JOIN app_users u ON u.id=m.user_id AND LOWER(u.role_name) LIKE '%admin%'
            WHERE (?='' OR LOWER(o.organization_name) LIKE ? OR LOWER(COALESCE(u.email,'')) LIKE ?)
              AND (?='' OR o.status=?)
            """, Long.class, search.trim(), pattern, pattern, normalizedStatus, normalizedStatus);
        return Map.of("content", organizationRows(search, normalizedStatus, safePage, safeSize), "totalElements", total,
                "page", safePage, "size", safeSize);
    }

    private List<Map<String,Object>> organizationRows(String search, String status, int page, int size) {
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return jdbc.queryForList("""
            SELECT o.public_id AS organizationId,o.organization_name AS organizationName,o.country_code AS country,
                   o.status,o.created_at AS registrationDate,o.gstin,
                   MAX(CASE WHEN LOWER(u.role_name) LIKE '%admin%' THEN u.name END) AS adminName,
                   MAX(CASE WHEN LOWER(u.role_name) LIKE '%admin%' THEN u.email END) AS adminEmail,
                   COUNT(DISTINCT m.user_id) AS totalUsers,
                   COALESCE(MAX(p.name),'Free') AS plan
            FROM organizations o
            LEFT JOIN organization_memberships m ON m.organization_id=o.id
            LEFT JOIN app_users u ON u.id=m.user_id
            LEFT JOIN organization_subscriptions s ON s.organization_id=o.id
            LEFT JOIN plans p ON p.id=s.plan_id
            WHERE (?='' OR LOWER(o.organization_name) LIKE ? OR LOWER(COALESCE(u.email,'')) LIKE ?)
              AND (?='' OR o.status=?)
            GROUP BY o.id,o.public_id,o.organization_name,o.country_code,o.status,o.created_at,o.gstin
            ORDER BY o.created_at DESC LIMIT ? OFFSET ?
            """, search.trim(), pattern, pattern, status, status, size, page * size);
    }

    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
}
