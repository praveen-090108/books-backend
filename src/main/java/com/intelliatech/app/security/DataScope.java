package com.intelliatech.app.security;

import java.util.Set;

public record DataScope(boolean unrestricted, Set<Long> accessibleUserIds) {
    public static DataScope unrestrictedScope() { return new DataScope(true, Set.of()); }
    public static DataScope restricted(Set<Long> userIds) { return new DataScope(false, Set.copyOf(userIds)); }
}
