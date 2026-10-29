package com.intelliatech.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.UserRequest;
import com.intelliatech.app.dto.response.UserResponse;
import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.entity.OrganizationMembership;
import com.intelliatech.app.entity.OrganizationRole;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.AppUserRepository;
import com.intelliatech.app.repository.BusinessRecordRepository;
import com.intelliatech.app.repository.OrganizationMembershipRepository;
import com.intelliatech.app.repository.OrganizationRoleRepository;
import com.intelliatech.app.security.TenantContext;
import com.intelliatech.app.security.TenantContextHolder;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Slf4j
public class AppUserService {
    private final AppUserRepository repository;
    private final BusinessRecordRepository businessRecords;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationRoleRepository organizationRoles;
    private final TenantContext tenants;
    private final com.intelliatech.app.repository.UserInvitationRepository invitations;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String search, Pageable pageable) {
        String query = search == null ? "" : search.trim().toLowerCase();
        List<UserResponse> users = memberships.findAllByOrganizationId(tenants.requireOrganizationId()).stream()
                .map(membership -> responseWithInvitation(membership.getUser(), membership.getRole().getName(), membership.getOrganization().getId()))
                .filter(user -> query.isBlank()
                        || contains(user.name(), query)
                        || contains(user.email(), query)
                        || contains(user.roleLabel(), query))
                .sorted(java.util.Comparator.comparing(UserResponse::name,
                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        int start = Math.min((int) pageable.getOffset(), users.size());
        int end = Math.min(start + pageable.getPageSize(), users.size());
        return new PageImpl<>(users.subList(start, end), pageable, users.size());
    }

    @Transactional public UserResponse update(Long id, UserRequest request) {
        AppUser user = tenantUser(id);
        repository.findByResourceId(request.resourceId()).filter(found -> !found.getId().equals(id))
                .ifPresent(found -> { throw new DuplicateResourceException("A user account already exists for the selected employee."); });
        apply(user, request, false);
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(tenants.requireOrganizationId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        OrganizationRole role = ensureRole(request.role());
        membership.setRole(role);
        memberships.save(membership);
        return response(repository.save(user), role.getName());
    }
    @Transactional public void delete(Long id) {
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(tenants.requireOrganizationId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        memberships.delete(membership);
        if (memberships.countByUserId(id) == 0) repository.delete(membership.getUser());
    }
    public AppUser get(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); }
    public AppUser byEmail(String email) { return repository.findForLoginEmail(email.trim()).orElseThrow(() -> new ResourceNotFoundException("User not found")); }

    private AppUser tenantUser(Long id) {
        return memberships.findByOrganizationIdAndUserId(tenants.requireOrganizationId(), id)
                .map(OrganizationMembership::getUser)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private OrganizationRole ensureRole(String roleName) {
        Long organizationId = tenants.requireOrganizationId();
        String code = roleCode(roleName);
        return organizationRoles.findByOrganizationIdAndCode(organizationId, code).orElseGet(() -> {
            OrganizationRole role = new OrganizationRole();
            role.setOrganization(memberships.findAllByOrganizationId(organizationId).stream().findFirst()
                    .map(OrganizationMembership::getOrganization)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization membership not found")));
            role.setCode(code);
            role.setName(roleName.trim());
            role.setPermissionsJson(code.equals("ORGANIZATION_ADMIN") ? "[\"*\"]" : "[]");
            role.setSystemRole(false);
            role.setActive(true);
            return organizationRoles.save(role);
        });
    }
    @Transactional
    public OrganizationRole resolveAssignableRole(String roleName) {
        Long organizationId=tenants.requireOrganizationId();
        String name=roleName==null?"":roleName.trim();
        if(name.isBlank())throw new IllegalArgumentException("Role is required.");
        var existing=organizationRoles.findByOrganizationIdAndNameIgnoreCase(organizationId,name).filter(OrganizationRole::isActive);
        if(existing.isPresent())return existing.get();
        businessRecords.findFirstByOrganizationIdAndModuleAndTypeAndPartyNameIgnoreCase(organizationId,"settings","roles",name)
                .filter(record->"Active".equalsIgnoreCase(record.getStatus()))
                .orElseThrow(()->new IllegalArgumentException("The selected role is not available for this organization."));
        OrganizationRole role=new OrganizationRole();
        role.setOrganization(memberships.findAllByOrganizationId(organizationId).stream().findFirst().map(OrganizationMembership::getOrganization).orElseThrow(()->new ResourceNotFoundException("Organization membership not found")));
        role.setCode(roleCode(name));role.setName(name);role.setPermissionsJson("[]");role.setSystemRole(false);role.setActive(true);return organizationRoles.save(role);
    }

    private String roleCode(String roleName) {
        if (roleName != null && roleName.toLowerCase().contains("admin")) return "ORGANIZATION_ADMIN";
        return roleName.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_|_$)", "");
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private void apply(AppUser user, UserRequest request, boolean creating) {
        var resource = businessRecords.findByModuleAndTypeAndId("resources", "resources", request.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Selected employee was not found"));
        if (resource.getPartyEmail() == null || resource.getPartyEmail().isBlank()) {
            throw new IllegalArgumentException("The selected employee must have an email address before a user account can be created.");
        }
        user.setResource(resource);
        user.setName(null);
        user.setEmail(null);
        user.setPhone(null);
        user.setDesignation(null);
        user.setRoleName(request.role().trim()); user.setStatus(request.status() == null ? "Active" : request.status());
        // Module permissions belong to the selected role. Keep no independent
        // per-user module list that could drift from the role configuration.
        user.setModuleAccess(null);
        // Reporting structure belongs to the Resource record. When that manager
        // has a user account, mirror the relationship for data-scope queries.
        var reportingManager = resource.getReportingManager();
        user.setManager(reportingManager == null
                ? null
                : repository.findByResourceId(reportingManager.getId()).orElse(null));
        if (request.password() != null && !request.password().isBlank()) user.setPasswordHash(passwordEncoder.encode(request.password()));
        else if (creating) throw new IllegalArgumentException("Password is required when creating a user");
    }
    public UserResponse response(AppUser user) {
        return response(user, user.getRoleName());
    }

    public UserResponse response(AppUser user, String organizationRoleName) {
        return response(user, organizationRoleName, TenantContextHolder.getOrganizationIdOrNull());
    }

    public UserResponse response(AppUser user, String organizationRoleName, Long organizationId) {
        List<String> permissions = permissionsFor(organizationId, organizationRoleName);
        var resource = user.getResource();
        String name = resource == null ? user.getName() : resource.getPartyName();
        String email = resource == null ? user.getEmail() : resource.getPartyEmail();
        String phone = resource == null ? user.getPhone() : resource.getPartyPhone();
        String designation = resource == null ? user.getDesignation() : resource.getCategory();
        return new UserResponse(user.getId(), resource == null ? null : resource.getId(), name, email, phone, designation,
                user.getManager() == null ? null : user.getManager().getId(), user.getManager() == null ? null : user.getManager().displayName(),
                normalizedRole(organizationRoleName), organizationRoleName, user.getStatus(), modulesFromPermissions(permissions), permissions,
                user.getPasswordHash() != null, user.isPlatformAdmin(), user.getCreatedAt(), user.getUpdatedAt(),null,null,null,null);
    }
    private UserResponse responseWithInvitation(AppUser user,String roleName,Long organizationId){
        UserResponse base=response(user,roleName,organizationId);
        var invitation=invitations.findFirstByOrganizationIdAndUserIdOrderByCreatedAtDesc(organizationId,user.getId()).orElse(null);
        String status=base.status();
        if(invitation!=null&&invitation.getStatus()==com.intelliatech.app.entity.UserInvitation.Status.PENDING&&invitation.getExpiresAt().isBefore(java.time.LocalDateTime.now()))status="Invitation Expired";
        return new UserResponse(base.id(),base.resourceId(),base.name(),base.email(),base.phone(),base.designation(),base.managerUserId(),base.managerName(),base.role(),base.roleLabel(),status,base.access(),base.permissions(),base.passwordConfigured(),base.platformAdmin(),base.createdAt(),base.updatedAt(),invitation==null?null:invitation.getDeliveryStatus().name(),invitation==null?null:invitation.getSentAt(),invitation==null?null:invitation.getAcceptedAt(),invitation==null?null:invitation.getExpiresAt());
    }
    private List<String> accessList(String access) {
        if (access == null || access.isBlank()) return List.of();
        return java.util.Arrays.stream(access.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    }
    private List<String> modulesFromPermissions(List<String> permissions) {
        if (permissions.contains("*")) return List.of("*");
        return permissions.stream()
                .map(value -> value.split("[:_]", 2)[0].trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
    private String normalizedRole(String role) {
        String value = role == null ? "user" : role.toLowerCase();
        if (value.contains("admin")) return "admin";
        if (value.contains("account")) return "accountant";
        if (value.contains("sales")) return "sales";
        return value.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
    @Transactional(readOnly = true)
    public List<String> permissionsFor(String roleName) {
        return permissionsFor(TenantContextHolder.getOrganizationIdOrNull(), roleName);
    }
    @Transactional(readOnly = true)
    public List<String> permissionsFor(Long organizationId, String roleName) {
        if (organizationId == null) return fallbackAccess(roleName);
        // Authentication happens before Hibernate's tenant identifier is
        // installed. Query by the explicit organization id so dynamic role
        // permissions are available in the login response as well as normal
        // authenticated requests, without ever crossing tenant boundaries.
        List<String> notes = jdbc.query(
                "SELECT notes FROM business_records WHERE organization_id=? AND module='settings' AND type='roles' AND LOWER(party_name)=LOWER(?) AND status='Active' ORDER BY id DESC LIMIT 1",
                (resultSet, rowNumber) -> resultSet.getString("notes"), organizationId, roleName);
        if (notes.isEmpty()) return fallbackAccess(roleName);
        try {
            JsonNode node = objectMapper.readTree(notes.get(0));
            JsonNode values = node.get("permissions");
            List<String> result = new ArrayList<>();
            if (values != null && values.isArray()) values.forEach(item -> result.add(item.asText()));
            return result;
        } catch (Exception exception) {
            log.warn("Invalid stored role permissions; using restricted fallback role={} exception={}",
                    normalizedRole(roleName), exception.getClass().getSimpleName());
            return fallbackAccess(roleName);
        }
    }
    private List<String> fallbackAccess(String roleName) {
        if (roleName != null && roleName.toLowerCase().contains("admin")) return List.of("*");
        return List.of();
    }
}
