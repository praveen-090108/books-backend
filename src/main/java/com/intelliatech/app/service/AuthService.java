package com.intelliatech.app.service;

import com.intelliatech.app.dto.request.*;
import com.intelliatech.app.dto.response.*;
import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.repository.AppUserRepository;
import com.intelliatech.app.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository repository; private final AppUserService users;
    private final PasswordEncoder encoder; private final JwtService jwtService;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = repository.findForLoginEmail(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!"Active".equalsIgnoreCase(user.getStatus()) || user.getPasswordHash() == null || !encoder.matches(request.password(), user.getPasswordHash()))
            throw new BadCredentialsException("Invalid email or password");
        return new AuthResponse(jwtService.generate(user.loginEmail()), users.response(user));
    }
    @Transactional public ForgotPasswordResponse forgot(ForgotPasswordRequest request) {
        var found = repository.findForLoginEmail(request.email().trim());
        if (found.isEmpty()) return new ForgotPasswordResponse("If the account exists, reset instructions are ready.", null);
        String token = randomToken(); AppUser user = found.get(); user.setResetTokenHash(hash(token));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(30)); repository.save(user);
        return new ForgotPasswordResponse("Use this secure link within 30 minutes to reset your password.", token);
    }
    @Transactional public void reset(ResetPasswordRequest request) {
        AppUser user = repository.findByResetTokenHash(hash(request.token()))
                .filter(value -> value.getResetTokenExpiresAt() != null && value.getResetTokenExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("Reset link is invalid or has expired"));
        user.setPasswordHash(encoder.encode(request.password())); user.setResetTokenHash(null); user.setResetTokenExpiresAt(null); repository.save(user);
    }
    private String randomToken() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
