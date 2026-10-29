package com.intelliatech.app.service;

import com.intelliatech.app.entity.BankAccountMaster;
import com.intelliatech.app.exception.ResourceNotFoundException;
import com.intelliatech.app.repository.BankAccountMasterRepository;
import com.intelliatech.app.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankAccountService {
    private final BankAccountMasterRepository accounts;
    private final TenantContext tenants;

    public BankAccountMaster selection(Long accountId, Long retainedAccountId) {
        if (accountId == null) return null;
        BankAccountMaster account = accounts.findByIdAndOrganizationId(accountId, tenants.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Bank Account not found."));
        if (!account.isActive() && !account.getId().equals(retainedAccountId)) {
            throw new IllegalArgumentException("Selected Bank Account is inactive.");
        }
        return account;
    }

    public boolean requiresAccount(String paymentMode) {
        if (paymentMode == null || paymentMode.isBlank()) return false;
        return !"cash".equalsIgnoreCase(paymentMode.trim()) && !"other".equalsIgnoreCase(paymentMode.trim());
    }

    public String displayName(BankAccountMaster account) {
        return account == null ? null : account.getAccountName();
    }
}
