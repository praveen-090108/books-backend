package com.intelliatech.app.security;

import java.security.Principal;

/** Keeps the immutable user id available without exposing it as the audit/display name. */
public record AuthenticatedUserPrincipal(Long userId, String loginEmail) implements Principal {
    @Override public String getName() { return loginEmail; }
}
