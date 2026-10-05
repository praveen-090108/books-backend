package com.intelliatech.app.security;

import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.repository.AppUserRepository;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DataScopeService {
    private final CurrentUserService currentUsers;
    private final AppUserRepository users;

    @Transactional(readOnly = true)
    public DataScope getCurrentDataScope() {
        AppUser current = currentUsers.getCurrentUser();
        if (currentUsers.isAdmin(current)) return DataScope.unrestrictedScope();

        Set<Long> accessible = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        accessible.add(current.getId());
        queue.add(current.getId());
        while (!queue.isEmpty()) {
            Long managerUserId = queue.removeFirst();
            AppUser manager = managerUserId.equals(current.getId())
                    ? current
                    : users.findById(managerUserId).orElse(null);
            if (manager == null) continue;
            // Resource.reportingManager is the authoritative hierarchy. Keep
            // the legacy user-manager relation as a migration-safe fallback.
            var subordinates = new LinkedHashMap<Long, AppUser>();
            users.findAllByManagerId(managerUserId)
                    .forEach(user -> subordinates.put(user.getId(), user));
            if (manager.getResource() != null) {
                users.findAllByResourceReportingManagerId(manager.getResource().getId())
                        .forEach(user -> subordinates.put(user.getId(), user));
            }
            for (AppUser subordinate : subordinates.values()) {
                if (accessible.add(subordinate.getId())) queue.add(subordinate.getId());
            }
        }
        return DataScope.restricted(accessible);
    }

    public void validateRecordAccess(Long createdBy) {
        DataScope scope = getCurrentDataScope();
        if (scope.unrestricted()) return;
        if (createdBy == null || !scope.accessibleUserIds().contains(createdBy)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to access this record.");
        }
    }
}
