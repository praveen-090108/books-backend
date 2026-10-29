package com.intelliatech.app.service;

import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.repository.AppUserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class LoginAttemptService {
    private final AppUserRepository users;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long userId) {
        users.findById(userId).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            users.save(user);
        });
    }
}
