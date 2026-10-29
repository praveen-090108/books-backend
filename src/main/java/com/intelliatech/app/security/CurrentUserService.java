package com.intelliatech.app.security;

import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.repository.AppUserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final AppUserRepository users;

    public AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required.");
        }
        return authenticatedUser(authentication)
                .orElseThrow(() -> new AccessDeniedException("The authenticated user account no longer exists."));
    }

    public Optional<AppUser> findCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return Optional.empty();
        return authenticatedUser(authentication);
    }

    public Long getCurrentUserId() { return getCurrentUser().getId(); }

    public boolean isAdmin(AppUser user) {
        return user.getRoleName() != null && user.getRoleName().toLowerCase().contains("admin");
    }

    private Optional<AppUser> authenticatedUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
            return users.findById(principal.userId());
        }
        return users.findAllForLoginEmail(authentication.getName()).stream().findFirst();
    }
}
