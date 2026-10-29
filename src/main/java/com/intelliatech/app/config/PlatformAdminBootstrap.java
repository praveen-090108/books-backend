package com.intelliatech.app.config;

import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.repository.AppUserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    @Value("${app.platform-admin.email:}") private String email;
    @Value("${app.platform-admin.username:}") private String username;
    @Value("${app.platform-admin.password:}") private String password;
    @Value("${app.platform-admin.name:IRAL Platform Administrator}") private String name;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) && !StringUtils.hasText(username) && !StringUtils.hasText(password)) return;
        if (!StringUtils.hasText(email) || !StringUtils.hasText(username) || !StringUtils.hasText(password))
            throw new IllegalStateException("Platform admin email, username and password must all be configured.");
        if (!password.matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}"))
            throw new IllegalStateException("Platform admin password must be at least 12 characters and contain uppercase, lowercase, number and special characters.");
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        AppUser user = users.findForLoginIdentifier(normalizedEmail).orElseGet(AppUser::new);
        if (user.getId() == null) {
            user.setName(name.trim()); user.setEmail(normalizedEmail); user.setUsername(normalizedUsername);
            user.setPasswordHash(encoder.encode(password)); user.setRoleName("Platform Super Admin");
        }
        user.setPlatformAdmin(true); user.setEmailVerified(true); user.setAccountStatus("ACTIVE"); user.setStatus("Active");
        users.save(user);
    }
}
