package com.intelliatech.app.security;

import com.intelliatech.app.entity.Organization;
import com.intelliatech.app.entity.OrganizationMembership;
import com.intelliatech.app.repository.OrganizationMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class TenantContext {
    private final CurrentUserService currentUsers;
    private final OrganizationMembershipRepository memberships;

    @Transactional(readOnly = true)
    public OrganizationMembership requireMembership() {
        Long organizationId = TenantContextHolder.requireOrganizationId();
        var user = currentUsers.getCurrentUser();
        return memberships.findByOrganizationIdAndUserId(organizationId, user.getId())
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .filter(value -> value.getOrganization().getStatus() == Organization.Status.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("An active organization membership is required."));
    }
    public Long requireOrganizationId() { return TenantContextHolder.requireOrganizationId(); }
}
