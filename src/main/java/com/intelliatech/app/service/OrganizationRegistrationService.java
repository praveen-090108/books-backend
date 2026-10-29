package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.OrganizationRegistrationRequest;
import com.intelliatech.app.entity.*;
import com.intelliatech.app.exception.ResourceConflictException;
import com.intelliatech.app.repository.*;
import com.intelliatech.app.util.SlugUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OrganizationRegistrationService {
    private final OrganizationRepository organizations;
    private final OrganizationRoleRepository roles;
    private final OrganizationMembershipRepository memberships;
    private final AppUserRepository users;
    private final PlanRepository plans;
    private final OrganizationSubscriptionRepository subscriptions;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    @Transactional
    public void register(OrganizationRegistrationRequest request) {
        String email = request.adminEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.adminUsername().trim().toLowerCase(Locale.ROOT);
        String gstin = normalizeOptional(request.gstin());
        String pan = normalizeOptional(request.pan());
        validatePassword(request.password(), request.confirmPassword());
        if (!users.findAllForLoginEmail(email).isEmpty()) {
            throw new ResourceConflictException("This email address is already registered with an organization. Each email address can belong to only one organization.");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResourceConflictException("This administrator username is already in use. Please choose another username.");
        }
        if (gstin != null && organizations.existsByGstinIgnoreCase(gstin)) {
            throw new ResourceConflictException("An organization with the supplied GSTIN already exists.");
        }

        Organization organization = new Organization();
        organization.setPublicId(UUID.randomUUID().toString());
        organization.setOrganizationName(request.organizationName().trim());
        organization.setLegalName(request.organizationName().trim());
        organization.setGstin(gstin);
        organization.setPan(pan);
        organization.setCountryCode(request.countryCode().trim().toUpperCase(Locale.ROOT));
        organization.setStatus(Organization.Status.PENDING_VERIFICATION);
        organization.setSlug(uniqueSlug(request.organizationName()));
        organization = organizations.save(organization);

        OrganizationRole role = new OrganizationRole();
        role.setOrganization(organization);
        role.setCode("ORGANIZATION_ADMIN");
        role.setName("Organization Admin");
        role.setPermissionsJson("[\"*\"]");
        role.setSystemRole(true);
        role = roles.save(role);

        AppUser user = new AppUser();
        user.setName(request.adminFullName().trim());
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerified(false);
        user.setAccountStatus("PENDING_VERIFICATION");
        user.setRoleName("Organization Admin");
        user.setStatus("Pending");
        user.setModuleAccess(null);
        user = users.save(user);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrganization(organization);
        membership.setUser(user);
        membership.setRole(role);
        membership.setStatus(OrganizationMembership.Status.PENDING);
        memberships.save(membership);

        jdbc.update("INSERT INTO organization_settings(organization_id,contact_email) VALUES (?,?)", organization.getId(), email);
        Plan free = plans.findByCodeAndActiveTrue("FREE")
                .orElseThrow(() -> new IllegalStateException("The Free plan has not been provisioned."));
        OrganizationSubscription subscription = new OrganizationSubscription();
        subscription.setOrganization(organization);
        subscription.setPlan(free);
        subscription.setStatus("ACTIVE");
        subscription.setStartedAt(LocalDateTime.now());
        subscriptions.save(subscription);

        issueVerification(user, organization);
    }

    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = verificationTokens.findForUpdateByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or has expired."));
        if (token.getConsumedAt() != null) {
            if (token.getUser().isEmailVerified()
                    && token.getOrganization().getStatus() == Organization.Status.ACTIVE) return;
            throw new IllegalArgumentException("Verification link is invalid or has expired.");
        }
        if (!token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification link is invalid or has expired.");
        }
        AppUser user = token.getUser();
        Organization organization = token.getOrganization();
        OrganizationMembership membership = memberships.findFirstByUserIdAndStatusOrderById(user.getId(), OrganizationMembership.Status.PENDING)
                .orElseThrow(() -> new IllegalStateException("The pending organization membership was not found."));
        token.setConsumedAt(LocalDateTime.now());
        user.setEmailVerified(true);
        user.setAccountStatus("ACTIVE");
        user.setStatus("Active");
        user.setTokenVersion(user.getTokenVersion() + 1);
        organization.setStatus(Organization.Status.ACTIVE);
        organization.setVerifiedAt(LocalDateTime.now());
        membership.setStatus(OrganizationMembership.Status.ACTIVE);
        membership.setJoinedAt(LocalDateTime.now());
        verificationTokens.save(token);
        users.save(user);
        organizations.save(organization);
        memberships.save(membership);
    }

    @Transactional
    public void resend(String requestedEmail) {
        String email = requestedEmail.trim().toLowerCase(Locale.ROOT);
        users.findForLoginIdentifier(email).filter(user -> !user.isEmailVerified()).ifPresent(user ->
                memberships.findFirstByUserIdAndStatusOrderById(user.getId(), OrganizationMembership.Status.PENDING)
                        .ifPresent(membership -> issueVerification(user, membership.getOrganization())));
    }

    @Transactional
    public void resendForOrganization(String publicId) {
        OrganizationMembership membership = memberships
                .findFirstByOrganizationPublicIdAndStatusOrderById(publicId, OrganizationMembership.Status.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("This organization does not have a pending administrator verification."));
        if (membership.getOrganization().getStatus() != Organization.Status.PENDING_VERIFICATION || membership.getUser().isEmailVerified()) {
            throw new IllegalArgumentException("This organization is not awaiting email verification.");
        }
        issueVerification(membership.getUser(), membership.getOrganization());
    }

    private void issueVerification(AppUser user, Organization organization) {
        verificationTokens.findAllByUserIdAndConsumedAtIsNull(user.getId()).forEach(existing -> {
            existing.setConsumedAt(LocalDateTime.now());
            verificationTokens.save(existing);
        });
        String raw = randomToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setOrganization(organization);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        verificationTokens.save(token);
        events.publishEvent(new VerificationEmailRequested(user.loginEmail(), user.displayName(), raw));
    }

    private String uniqueSlug(String name) {
        String base = SlugUtil.toSlug(name);
        if (base.isBlank()) base = "organization";
        base = base.substring(0, Math.min(base.length(), 82));
        String candidate = base;
        while (organizations.existsBySlug(candidate)) candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        return candidate;
    }
    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }
    private void validatePassword(String password, String confirmation) {
        if (!password.equals(confirmation)) throw new IllegalArgumentException("Password and confirmation do not match.");
        if (!password.matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}")) {
            throw new IllegalArgumentException("Password must contain uppercase, lowercase, number and special characters and be at least 12 characters.");
        }
    }
    private String randomToken() { byte[] value = new byte[32]; new SecureRandom().nextBytes(value); return HexFormat.of().formatHex(value); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Unable to secure verification token.", exception); }
    }
}
