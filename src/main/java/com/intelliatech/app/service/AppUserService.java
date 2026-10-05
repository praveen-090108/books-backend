package com.intelliatech.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliatech.app.dto.request.UserRequest;
import com.intelliatech.app.dto.response.UserResponse;
import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.AppUserRepository;
import com.intelliatech.app.repository.BusinessRecordRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AppUserService {
    private final AppUserRepository repository;
    private final BusinessRecordRepository businessRecords;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String search, Pageable pageable) {
        return repository.findAll((root, query, cb) -> {
            if (search == null || search.isBlank()) return cb.conjunction();
            String like = "%" + search.trim().toLowerCase() + "%";
            var resource = root.join("resource", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.or(cb.like(cb.lower(resource.get("partyName")), like), cb.like(cb.lower(resource.get("partyEmail")), like), cb.like(cb.lower(root.get("email")), like), cb.like(cb.lower(root.get("roleName")), like));
        }, pageable).map(this::response);
    }

    @Transactional public UserResponse create(UserRequest request) {
        if (request.password() == null || request.password().isBlank()) throw new IllegalArgumentException("Password is required when creating a user");
        if (repository.existsByResourceId(request.resourceId())) throw new DuplicateResourceException("A user account already exists for the selected employee.");
        AppUser user = new AppUser(); apply(user, request, true); return response(repository.save(user));
    }

    @Transactional public UserResponse update(Long id, UserRequest request) {
        AppUser user = get(id);
        repository.findByResourceId(request.resourceId()).filter(found -> !found.getId().equals(id))
                .ifPresent(found -> { throw new DuplicateResourceException("A user account already exists for the selected employee."); });
        apply(user, request, false); return response(repository.save(user));
    }
    @Transactional public void delete(Long id) { repository.delete(get(id)); }
    public AppUser get(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); }
    public AppUser byEmail(String email) { return repository.findForLoginEmail(email.trim()).orElseThrow(() -> new ResourceNotFoundException("User not found")); }

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
        List<String> permissions = permissionsFor(user.getRoleName());
        var resource = user.getResource();
        String name = resource == null ? user.getName() : resource.getPartyName();
        String email = resource == null ? user.getEmail() : resource.getPartyEmail();
        String phone = resource == null ? user.getPhone() : resource.getPartyPhone();
        String designation = resource == null ? user.getDesignation() : resource.getCategory();
        return new UserResponse(user.getId(), resource == null ? null : resource.getId(), name, email, phone, designation,
                user.getManager() == null ? null : user.getManager().getId(), user.getManager() == null ? null : user.getManager().displayName(),
                normalizedRole(user.getRoleName()), user.getRoleName(), user.getStatus(), modulesFromPermissions(permissions), permissions,
                user.getPasswordHash() != null, user.getCreatedAt(), user.getUpdatedAt());
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
        var role = businessRecords.findFirstByModuleAndTypeAndPartyNameIgnoreCase("settings", "roles", roleName);
        if (role.isEmpty()) return fallbackAccess(roleName);
        try {
            JsonNode node = objectMapper.readTree(role.get().getNotes());
            JsonNode values = node.get("permissions");
            List<String> result = new ArrayList<>();
            if (values != null && values.isArray()) values.forEach(item -> result.add(item.asText()));
            return result;
        } catch (Exception ignored) { return fallbackAccess(roleName); }
    }
    private List<String> fallbackAccess(String roleName) {
        if (roleName != null && roleName.toLowerCase().contains("admin")) return List.of("*");
        return List.of();
    }
}
