package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.*;
import com.intelliatech.app.dto.response.*;
import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.repository.AppUserRepository;
import com.intelliatech.app.repository.OrganizationMembershipRepository;
import com.intelliatech.app.entity.Organization;
import com.intelliatech.app.entity.OrganizationMembership;
import com.intelliatech.app.security.JwtService;
import com.intelliatech.app.security.TenantContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

@Service @RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository repository; private final AppUserService users;
    private final OrganizationMembershipRepository memberships;
    private final PasswordEncoder encoder; private final JwtService jwtService;
    private final LoginAttemptService loginAttempts; private final ApplicationEventPublisher events;
    private final com.intelliatech.app.security.CurrentUserService currentUsers;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        List<AppUser> candidates = repository.findAllForLoginIdentifier(request.email().trim());
        if (candidates.isEmpty()) throw new BadCredentialsException("Invalid email or password.");
        List<LoginCandidate> matches = new ArrayList<>();
        boolean passwordMatchedLockedAccount = false;
        boolean pendingInvitation = false;
        for (AppUser candidate : candidates) {
            pendingInvitation |= "Pending Invitation".equalsIgnoreCase(candidate.getStatus())
                    || "PENDING_INVITATION".equalsIgnoreCase(candidate.getAccountStatus());
            if (candidate.getPasswordHash() == null || !encoder.matches(request.password(), candidate.getPasswordHash())) continue;
            if (candidate.getLockedUntil() != null && candidate.getLockedUntil().isAfter(LocalDateTime.now())) {
                passwordMatchedLockedAccount = true;
                continue;
            }
            memberships.findFirstByUserIdAndStatusOrderById(candidate.getId(), OrganizationMembership.Status.ACTIVE)
                    .filter(value -> value.getOrganization().getStatus() == Organization.Status.ACTIVE)
                    .filter(value -> "Active".equalsIgnoreCase(candidate.getStatus()))
                    .filter(value -> "ACTIVE".equalsIgnoreCase(candidate.getAccountStatus()))
                    .filter(value -> candidate.isEmailVerified())
                    .ifPresent(membership -> matches.add(new LoginCandidate(candidate, membership)));
        }
        if (matches.isEmpty()) {
            if (passwordMatchedLockedAccount) throw new BadCredentialsException("This account is temporarily locked after repeated failed sign-in attempts. Please try again in 15 minutes.");
            if (pendingInvitation) throw new BadCredentialsException("Your invitation is pending. Open the invitation email to activate your account.");
            if (candidates.size() == 1) {
                AppUser candidate = candidates.get(0);
                if (candidate.getLockedUntil() == null || !candidate.getLockedUntil().isAfter(LocalDateTime.now())) {
                    loginAttempts.failed(candidate.getId());
                }
            }
            throw new BadCredentialsException("Invalid email or password.");
        }
        if (matches.size() > 1) throw new BadCredentialsException("This email is linked to multiple organizations. Sign in with your username.");
        AppUser user = matches.get(0).user();
        OrganizationMembership membership = matches.get(0).membership();
        if (encoder.upgradeEncoding(user.getPasswordHash())) user.setPasswordHash(encoder.encode(request.password()));
        user.setFailedLoginAttempts(0); user.setLockedUntil(null); user.setLastLoginAt(LocalDateTime.now()); repository.save(user);
        return new AuthResponse(jwtService.generate(user, membership),
                users.response(user, membership.getRole().getName(), membership.getOrganization().getId()),
                membership.getOrganization().getId(), membership.getOrganization().getOrganizationName());
    }
    @Transactional
    public AuthResponse platformLogin(LoginRequest request) {
        AppUser user = repository.findAllForLoginIdentifier(request.email().trim()).stream()
                .filter(AppUser::isPlatformAdmin)
                .filter(value -> value.getPasswordHash() != null && encoder.matches(request.password(), value.getPasswordHash()))
                .findFirst()
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) throw new BadCredentialsException("Invalid email or password");
        if (!"Active".equalsIgnoreCase(user.getStatus()) || !"ACTIVE".equalsIgnoreCase(user.getAccountStatus())
                || !user.isEmailVerified() || user.getPasswordHash() == null || !encoder.matches(request.password(), user.getPasswordHash())) {
            loginAttempts.failed(user.getId());
            throw new BadCredentialsException("Invalid email or password");
        }
        user.setFailedLoginAttempts(0); user.setLockedUntil(null); user.setLastLoginAt(LocalDateTime.now()); repository.save(user);
        return new AuthResponse(jwtService.generatePlatformAdmin(user), users.response(user), null, null);
    }
    @Transactional(readOnly = true)
    public UserResponse currentSession() {
        AppUser user = currentUsers.getCurrentUser();
        Long organizationId = TenantContextHolder.requireOrganizationId();
        OrganizationMembership membership = memberships.findByOrganizationIdAndUserId(organizationId, user.getId())
                .filter(value -> value.getStatus() == OrganizationMembership.Status.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("An active organization membership is required."));
        return users.response(user, membership.getRole().getName(), organizationId);
    }
    @Transactional public ForgotPasswordResponse forgot(ForgotPasswordRequest request) {
        var found = repository.findAllForLoginEmail(request.email().trim());
        if (found.size() != 1) return new ForgotPasswordResponse("If the account exists, reset instructions are ready.", null);
        if ("Pending Invitation".equalsIgnoreCase(found.get(0).getStatus())) return new ForgotPasswordResponse("Your account invitation is still pending. Ask your organization administrator to resend it.", null);
        String token = randomToken(); AppUser user = found.get(0); user.setResetTokenHash(hash(token));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(30)); repository.save(user);
        events.publishEvent(new PasswordResetEmailRequested(user.loginEmail(), user.displayName(), token));
        return new ForgotPasswordResponse("If the account exists, reset instructions will be emailed.", null);
    }
    @Transactional public void reset(ResetPasswordRequest request) {
        if (!request.password().matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}")) {
            throw new IllegalArgumentException("Password must contain uppercase, lowercase, number and special characters and be at least 12 characters.");
        }
        AppUser user = repository.findByResetTokenHash(hash(request.token()))
                .filter(value -> value.getResetTokenExpiresAt() != null && value.getResetTokenExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("Reset link is invalid or has expired"));
        user.setPasswordHash(encoder.encode(request.password())); user.setResetTokenHash(null); user.setResetTokenExpiresAt(null);
        user.setTokenVersion(user.getTokenVersion() + 1); user.setFailedLoginAttempts(0); user.setLockedUntil(null); repository.save(user);
    }
    @Transactional public void changePassword(ChangePasswordRequest request){
        AppUser user=currentUsers.getCurrentUser();
        if(user.getPasswordHash()==null||!encoder.matches(request.currentPassword(),user.getPasswordHash()))throw new IllegalArgumentException("Current password is incorrect.");
        if(!request.newPassword().matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}"))throw new IllegalArgumentException("Password must contain uppercase, lowercase, number and special characters and be at least 12 characters.");
        user.setPasswordHash(encoder.encode(request.newPassword()));user.setTokenVersion(user.getTokenVersion()+1);repository.save(user);
    }
    private String randomToken() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private record LoginCandidate(AppUser user, OrganizationMembership membership) {}
}
