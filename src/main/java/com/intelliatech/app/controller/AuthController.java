package com.intelliatech.app.controller;

import com.intelliatech.app.config.SaasProperties;
import com.intelliatech.app.dto.request.*;
import com.intelliatech.app.dto.response.*;
import com.intelliatech.app.service.AuthRateLimitService;
import com.intelliatech.app.service.AuthService;
import com.intelliatech.app.service.OrganizationRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService service;
    private final OrganizationRegistrationService registrations;
    private final AuthRateLimitService rateLimits;
    private final SaasProperties saas;
    private final com.intelliatech.app.service.UserInvitationService invitations;

    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimits.check("login", client(http) + ":" + request.email(), 10, Duration.ofMinutes(15)); return service.login(request);
    }
    @PostMapping("/platform/login") public AuthResponse platformLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimits.check("platform-login", client(http) + ":" + request.email(), 10, Duration.ofMinutes(15));
        return service.platformLogin(request);
    }
    @PostMapping("/forgot-password") public ForgotPasswordResponse forgot(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        rateLimits.check("forgot", client(http) + ":" + request.email(), 5, Duration.ofHours(1)); return service.forgot(request);
    }
    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        rateLimits.check("reset", client(http), 10, Duration.ofMinutes(15)); service.reset(request);
    }
    @GetMapping("/invitations/validate") public InvitationDetailsResponse validateInvitation(@RequestParam String token, HttpServletRequest http) {
        rateLimits.check("invitation-validate", client(http), 30, Duration.ofMinutes(15)); return invitations.validate(token);
    }
    @PostMapping("/invitations/accept") @ResponseStatus(HttpStatus.NO_CONTENT) public void acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request, HttpServletRequest http) {
        rateLimits.check("invitation-accept", client(http), 10, Duration.ofMinutes(15)); invitations.accept(request);
    }
    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void changePassword(@Valid @RequestBody ChangePasswordRequest request){service.changePassword(request);}
    @GetMapping("/session") public UserResponse session(){return service.currentSession();}
    @PostMapping("/register-organization") @ResponseStatus(HttpStatus.ACCEPTED)
    public PublicAuthMessageResponse register(@Valid @RequestBody OrganizationRegistrationRequest request, HttpServletRequest http) {
        requireRegistration(); rateLimits.check("register", client(http), 5, Duration.ofHours(1)); registrations.register(request);
        return new PublicAuthMessageResponse("Registration received. Check your email to verify and activate your organization.");
    }
    @PostMapping("/verify-email") public PublicAuthMessageResponse verify(@Valid @RequestBody EmailVerificationRequest request, HttpServletRequest http) {
        requireRegistration(); rateLimits.check("verify", client(http), 20, Duration.ofMinutes(15)); registrations.verify(request.token());
        return new PublicAuthMessageResponse("Email verified. Your IRAL organization is now active.");
    }
    @PostMapping("/resend-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    public PublicAuthMessageResponse resend(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest http) {
        requireRegistration(); rateLimits.check("resend", client(http) + ":" + request.email(), 5, Duration.ofHours(1)); registrations.resend(request.email());
        return new PublicAuthMessageResponse("If a pending registration exists, a new verification email will be sent.");
    }
    private void requireRegistration() {
        if (!saas.registrationEnabled()) throw new IllegalStateException("Organization registration is not enabled in this environment.");
    }
    private String client(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
    }
}
