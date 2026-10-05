package com.intelliatech.app.controller;

import com.intelliatech.app.entity.ItemCategoryMaster;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.ItemCategoryMasterRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/item-categories")
@RequiredArgsConstructor
public class ItemCategoryMasterController {
    private static final Long ORGANIZATION_ID = 1L;
    private final ItemCategoryMasterRepository categories;
    private final JdbcTemplate jdbc;

    public record Request(@NotBlank @Size(max=120) String categoryName, @Size(max=500) String description, boolean active, Integer displayOrder) {}
    public record Response(Long id, String categoryName, String description, boolean active, Integer displayOrder, long usageCount) {}

    @GetMapping @PreAuthorize("isAuthenticated()")
    public List<Response> list(@RequestParam(defaultValue="false") boolean includeInactive) {
        var rows = includeInactive ? categories.findAllByOrganizationIdOrderByDisplayOrderAscCategoryNameAsc(ORGANIZATION_ID)
                : categories.findAllByOrganizationIdAndActiveTrueOrderByDisplayOrderAscCategoryNameAsc(ORGANIZATION_ID);
        return rows.stream().map(this::response).toList();
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response create(@Valid @RequestBody Request request) {
        String name = request.categoryName().trim();
        if (categories.existsByOrganizationIdAndCategoryNameIgnoreCase(ORGANIZATION_ID, name)) throw new DuplicateResourceException("Item Category already exists.");
        ItemCategoryMaster category = new ItemCategoryMaster();
        category.setOrganizationId(ORGANIZATION_ID); category.setCategoryName(name); category.setDescription(clean(request.description()));
        category.setActive(request.active()); category.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        category.setCreatedBy(user()); category.setUpdatedBy(user());
        return response(categories.save(category));
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        ItemCategoryMaster category = get(id); String name = request.categoryName().trim();
        if (categories.existsByOrganizationIdAndCategoryNameIgnoreCaseAndIdNot(ORGANIZATION_ID, name, id)) throw new DuplicateResourceException("Item Category already exists.");
        String previousName = category.getCategoryName();
        category.setCategoryName(name); category.setDescription(clean(request.description())); category.setActive(request.active());
        category.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder()); category.setUpdatedBy(user());
        ItemCategoryMaster saved = categories.save(category);
        if (!previousName.equals(name)) {
            jdbc.update("UPDATE business_records SET category=? WHERE module='purchases' AND type='items' AND LOWER(category)=LOWER(?)", name, previousName);
        }
        return response(saved);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void delete(@PathVariable Long id) {
        ItemCategoryMaster category = get(id);
        if (usage(category.getCategoryName()) > 0) { category.setActive(false); category.setUpdatedBy(user()); categories.save(category); }
        else categories.delete(category);
    }

    private ItemCategoryMaster get(Long id) { return categories.findByIdAndOrganizationId(id, ORGANIZATION_ID).orElseThrow(() -> new ResourceNotFoundException("Item Category not found.")); }
    private Response response(ItemCategoryMaster value) { return new Response(value.getId(), value.getCategoryName(), value.getDescription(), value.isActive(), value.getDisplayOrder(), usage(value.getCategoryName())); }
    private long usage(String name) { return jdbc.queryForObject("SELECT COUNT(*) FROM business_records WHERE module='purchases' AND type='items' AND LOWER(category)=LOWER(?)", Long.class, name); }
    private String user() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
