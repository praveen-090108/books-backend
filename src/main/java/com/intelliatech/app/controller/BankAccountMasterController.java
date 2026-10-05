package com.intelliatech.app.controller;

import com.intelliatech.app.entity.BankAccountMaster;
import com.intelliatech.app.exception.DuplicateResourceException;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BankAccountMasterRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/masters/bank-accounts")
@RequiredArgsConstructor
public class BankAccountMasterController {
    private static final Long ORGANIZATION_ID = 1L;
    private final BankAccountMasterRepository accounts;
    private final JdbcTemplate jdbc;

    public record Request(
            @NotBlank @Size(max=160) String accountName,
            @NotBlank @Size(max=160) String bankName,
            @Size(max=160) String accountHolderName,
            @Size(max=128) String accountNumber,
            @Size(max=40) String accountType,
            @Size(max=32) String ifscCode,
            @Size(max=160) String branchName,
            @NotBlank @Pattern(regexp="^[A-Za-z]{3}$") String currencyCode,
            BigDecimal openingBalance,
            boolean active,
            @Size(max=1000) String notes) {}
    public record Response(Long id, String accountName, String bankName, String accountHolderName,
                           String maskedAccountNumber, String accountType, String ifscCode, String branchName,
                           String currencyCode, BigDecimal openingBalance, boolean active, String notes, long usageCount) {}

    @GetMapping @PreAuthorize("isAuthenticated()")
    public List<Response> list(@RequestParam(defaultValue="false") boolean includeInactive) {
        var rows = includeInactive ? accounts.findAllByOrganizationIdOrderByAccountNameAsc(ORGANIZATION_ID)
                : accounts.findAllByOrganizationIdAndActiveTrueOrderByAccountNameAsc(ORGANIZATION_ID);
        return rows.stream().map(this::response).toList();
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response create(@Valid @RequestBody Request request) {
        String name = request.accountName().trim();
        if (accounts.existsByOrganizationIdAndAccountNameIgnoreCase(ORGANIZATION_ID, name)) throw new DuplicateResourceException("Bank Account already exists.");
        BankAccountMaster account = new BankAccountMaster(); account.setOrganizationId(ORGANIZATION_ID);
        copy(request, account); account.setCreatedBy(user()); account.setUpdatedBy(user());
        return response(accounts.save(account));
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        BankAccountMaster account = get(id); String name = request.accountName().trim();
        if (accounts.existsByOrganizationIdAndAccountNameIgnoreCaseAndIdNot(ORGANIZATION_ID, name, id)) throw new DuplicateResourceException("Bank Account already exists.");
        String retainedNumber = account.getAccountNumber(); copy(request, account);
        if (clean(request.accountNumber()) == null) account.setAccountNumber(retainedNumber);
        account.setUpdatedBy(user()); return response(accounts.save(account));
    }

    @PatchMapping("/{id}/status") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public Response status(@PathVariable Long id, @RequestParam boolean active) {
        BankAccountMaster account = get(id); account.setActive(active); account.setUpdatedBy(user());
        return response(accounts.save(account));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void delete(@PathVariable Long id) {
        BankAccountMaster account = get(id);
        if (usageCount(id) > 0) { account.setActive(false); account.setUpdatedBy(user()); accounts.save(account); }
        else accounts.delete(account);
    }

    private void copy(Request request, BankAccountMaster account) {
        account.setAccountName(request.accountName().trim()); account.setBankName(request.bankName().trim());
        account.setAccountHolderName(clean(request.accountHolderName())); account.setAccountNumber(clean(request.accountNumber()));
        account.setAccountType(clean(request.accountType())); account.setIfscCode(upper(request.ifscCode()));
        account.setBranchName(clean(request.branchName())); account.setCurrencyCode(request.currencyCode().trim().toUpperCase());
        account.setOpeningBalance(request.openingBalance()); account.setActive(request.active()); account.setNotes(clean(request.notes()));
    }
    private Response response(BankAccountMaster value) { return new Response(value.getId(), value.getAccountName(), value.getBankName(), value.getAccountHolderName(), mask(value.getAccountNumber()), value.getAccountType(), value.getIfscCode(), value.getBranchName(), value.getCurrencyCode(), value.getOpeningBalance(), value.isActive(), value.getNotes(), usageCount(value.getId())); }
    private long usageCount(Long id) { return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM expenses WHERE bank_account_id=? AND deleted=FALSE)+(SELECT COUNT(*) FROM bill_payments WHERE bank_account_id=?)+(SELECT COUNT(*) FROM invoice_payments WHERE bank_account_id=?)", Long.class, id, id, id); }
    private BankAccountMaster get(Long id) { return accounts.findByIdAndOrganizationId(id, ORGANIZATION_ID).orElseThrow(() -> new ResourceNotFoundException("Bank Account not found.")); }
    private String user() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private String upper(String value) { String cleaned=clean(value); return cleaned==null?null:cleaned.toUpperCase(); }
    private String mask(String value) { if (value==null||value.isBlank()) return "-"; String compact=value.replaceAll("\\s",""); return "••••"+compact.substring(Math.max(0,compact.length()-4)); }
}
