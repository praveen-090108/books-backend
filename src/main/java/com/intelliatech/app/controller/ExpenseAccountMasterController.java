package com.intelliatech.app.controller;

import com.intelliatech.app.entity.ExpenseAccountMaster;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.ExpenseAccountMasterRepository;
import com.intelliatech.app.repository.ExpenseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/expense-accounts")
@RequiredArgsConstructor
public class ExpenseAccountMasterController {
    private static final Long ORGANIZATION_ID = 1L;
    private final ExpenseAccountMasterRepository accounts;
    private final ExpenseRepository expenses;

    public record Request(@NotBlank @Size(max=120) String accountName, @Size(max=500) String description, boolean active, Integer displayOrder) {}
    public record Response(Long id, String accountName, String description, boolean active, Integer displayOrder, long usageCount) {}

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Response> list(@RequestParam(defaultValue="false") boolean includeInactive) {
        var rows = includeInactive
                ? accounts.findAllByOrganizationIdOrderByDisplayOrderAscAccountNameAsc(ORGANIZATION_ID)
                : accounts.findAllByOrganizationIdAndActiveTrueOrderByDisplayOrderAscAccountNameAsc(ORGANIZATION_ID);
        return rows.stream().map(this::response).toList();
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response create(@Valid @RequestBody Request request) {
        String name = request.accountName().trim();
        if (accounts.existsByOrganizationIdAndAccountNameIgnoreCase(ORGANIZATION_ID, name)) throw new DuplicateResourceException("Expense Account already exists.");
        ExpenseAccountMaster account = new ExpenseAccountMaster();
        account.setOrganizationId(ORGANIZATION_ID); account.setAccountName(name); account.setDescription(clean(request.description()));
        account.setActive(request.active()); account.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        account.setCreatedBy(user()); account.setUpdatedBy(user());
        return response(accounts.save(account));
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        ExpenseAccountMaster account = get(id); String name = request.accountName().trim();
        if (accounts.existsByOrganizationIdAndAccountNameIgnoreCaseAndIdNot(ORGANIZATION_ID, name, id)) throw new DuplicateResourceException("Expense Account already exists.");
        account.setAccountName(name); account.setDescription(clean(request.description())); account.setActive(request.active());
        account.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder()); account.setUpdatedBy(user());
        return response(accounts.save(account));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void delete(@PathVariable Long id) {
        ExpenseAccountMaster account = get(id);
        if (expenses.countByExpenseAccountMasterId(id) > 0) { account.setActive(false); account.setUpdatedBy(user()); accounts.save(account); }
        else accounts.delete(account);
    }

    private ExpenseAccountMaster get(Long id) { return accounts.findByIdAndOrganizationId(id, ORGANIZATION_ID).orElseThrow(() -> new ResourceNotFoundException("Expense Account not found.")); }
    private Response response(ExpenseAccountMaster value) { return new Response(value.getId(), value.getAccountName(), value.getDescription(), value.isActive(), value.getDisplayOrder(), expenses.countByExpenseAccountMasterId(value.getId())); }
    private String user() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
