package com.intelliatech.app.controller;

import com.intelliatech.app.entity.LeadSourceMaster;
import com.intelliatech.app.repository.LeadSourceMasterRepository;
import com.intelliatech.app.security.CurrentUserService;
import com.intelliatech.app.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/lead-sources")
@RequiredArgsConstructor
public class LeadSourceMasterController {
    private final LeadSourceMasterRepository repository;
    private final CurrentUserService users;
    private final JdbcTemplate jdbc;
    private final TenantContext tenants;

    public record Request(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            boolean active,
            Integer displayOrder) {}

    public record Response(
            Long id,
            String name,
            String description,
            boolean active,
            int displayOrder,
            long usageCount) {}

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Response> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        List<LeadSourceMaster> items = includeInactive
                ? repository.findByOrganizationIdOrderByDisplayOrderAscNameAsc(org())
                : repository.findByOrganizationIdAndActiveTrueOrderByDisplayOrderAscNameAsc(org());
        return items.stream().map(this::response).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Response create(@Valid @RequestBody Request request) {
        LeadSourceMaster item = new LeadSourceMaster();
        copy(item, request);
        item.setCreatedBy(users.getCurrentUserId());
        item.setUpdatedBy(users.getCurrentUserId());
        return response(repository.save(item));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        LeadSourceMaster item = get(id);
        String previousName = item.getName();
        copy(item, request);
        item.setUpdatedBy(users.getCurrentUserId());
        LeadSourceMaster saved = repository.save(item);
        if (!previousName.equals(saved.getName())) {
            renameReferences(previousName, saved.getName());
        }
        return response(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(@PathVariable Long id) {
        LeadSourceMaster item = get(id);
        if (usage(item.getName()) > 0) {
            item.setActive(false);
            item.setUpdatedBy(users.getCurrentUserId());
            repository.save(item);
        } else {
            repository.delete(item);
        }
    }

    private LeadSourceMaster get(Long id) {
        return repository.findById(id)
                .filter(item -> item.getOrganizationId().equals(org()))
                .orElseThrow(() -> new IllegalArgumentException("Lead Source does not exist."));
    }

    private void copy(LeadSourceMaster item, Request request) {
        String name = request.name().trim();
        repository.findByOrganizationIdAndNameIgnoreCase(org(), name)
                .filter(existing -> !existing.getId().equals(item.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("A Lead Source with this name already exists.");
                });
        item.setOrganizationId(org());
        item.setName(name);
        item.setDescription(request.description() == null ? null : request.description().trim());
        item.setActive(request.active());
        item.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    private void renameReferences(String previousName, String newName) {
        String actor = users.getCurrentUser().loginEmail();
        jdbc.update("UPDATE leads SET source=?, updated_by=? WHERE organization_id=? AND source=?",
                newName, actor, org(), previousName);
        jdbc.update("UPDATE lead_companies SET lead_source=?, updated_by=? WHERE organization_id=? AND lead_source=?",
                newName, actor, org(), previousName);
        jdbc.update("UPDATE lead_contacts SET lead_source=?, updated_by=? WHERE organization_id=? AND lead_source=?",
                newName, actor, org(), previousName);
    }

    private long usage(String name) {
        Long count = jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM leads WHERE organization_id=? AND deleted=FALSE AND source=?) +
                  (SELECT COUNT(*) FROM lead_companies WHERE organization_id=? AND deleted=FALSE AND lead_source=?) +
                  (SELECT COUNT(*) FROM lead_contacts WHERE organization_id=? AND deleted=FALSE AND lead_source=?)
                """, Long.class, org(), name, org(), name, org(), name);
        return count == null ? 0 : count;
    }

    private Long org() {
        return tenants.requireOrganizationId();
    }

    private Response response(LeadSourceMaster item) {
        return new Response(item.getId(), item.getName(), item.getDescription(), item.isActive(),
                item.getDisplayOrder(), usage(item.getName()));
    }
}
