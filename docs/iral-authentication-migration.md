# IRAL Authentication Migration

Date: 2026-10-23

## Current state

- `app_users` supports BCrypt password hashes and status.
- Login accepts email/password and returns an HMAC JWT with email as subject.
- The frontend stores the token and user object in local storage.
- Forgot-password stores a SHA-256 token hash with a 30-minute expiry, but returns the raw token in the response.
- `DefaultAdminInitializer` contains `admin@intelliatech.com` and `admin123`; sandbox enables it.
- Login UI pre-fills the same credentials.
- `DemoTokenAuthenticationFilter` recognizes `demo-token-*` tokens in source.
- There is no email verification, username, auth throttling, refresh-session store, MFA, tenant membership or organization-status enforcement.

## Target identity model

`app_users` becomes a global identity table. Add normalized email/username, email verification state, account status, password algorithm/version, token version, failed login counters and security timestamps. Keep employee/resource data separate.

Organization authorization comes from an active `organization_membership`, not a user-supplied organization ID. Platform administrators use a separate platform role/MFA association and do not require an organization membership.

## Safe migration sequence

1. Inventory existing users, duplicate case-insensitive emails, null hashes, resource links and role names.
2. Create the approved legacy organization and membership/role records without changing login behavior.
3. Associate every current user with the legacy organization; flag ambiguous users for manual review.
4. Introduce new principal claims and tenant/status checks behind a feature flag while accepting existing valid tokens for a short compatibility window.
5. Add server-tracked sessions/token versions and force reauthentication at cutover.
6. Remove frontend credential defaults and disable/delete hardcoded admin bootstrap after verifying the migrated admin hash and recovery method.
7. Remove the reset token from API responses; deliver it through the configured email provider only.
8. Remove/demo-profile-isolate demo token support.
9. Enable registration only after verification delivery, rate limits, tenant provisioning and isolation tests pass.

## Password strategy

Use Spring Security Argon2id for newly set passwords with parameters benchmarked for the deployment. Retain BCrypt verification for existing hashes. On a successful BCrypt login, encode the supplied password with Argon2id and replace the old hash in the same transaction. Passwords never enter logs, audit metadata or analytics.

## Verification and reset challenges

Store only a keyed token hash with user ID, organization ID where applicable, purpose, expiry, consumed timestamp, attempt count and request metadata. Tokens are random, single-use and purpose-bound. Consume with a row lock. Resend invalidates prior active verification challenges and is rate-limited.

Responses remain generic. No endpoint returns the raw verification or reset token outside an explicit test-only adapter.

## Session design

Preferred web design:

- short-lived signed access token in `Secure; HttpOnly; SameSite=Lax/Strict` cookie;
- opaque rotating refresh token, also HttpOnly, whose hash/session row is stored server-side;
- CSRF token/header for state-changing cookie-authenticated requests;
- session ID, identity type, user ID, membership ID, organization ID and security versions in validated claims;
- logout, password reset, role change, membership suspension and organization suspension revoke affected sessions.

If deployment constraints require temporary bearer tokens, remove local-storage persistence where possible and still add session IDs, revocation and organization status lookup.

## Bootstrap platform administrator

Provision only when explicit environment flags and all required secrets are present. Accept email and a one-time activation secret through environment/secret management, create exactly one inactive bootstrap record idempotently, require activation/password change and MFA enrollment, then disable bootstrap. Never seed a source-controlled password.

## Cutover validation

- migrated administrator can authenticate with the existing approved password;
- no hardcoded login works;
- inactive/unverified/suspended identities fail generically;
- suspended organization tokens fail immediately;
- reset/verification tokens expire and cannot be replayed;
- login/forgot/resend endpoints throttle abuse;
- role and tenant changes invalidate prior authorization;
- platform and organization route trees reject the other principal type;
- audit logs contain no token or password material.

## Rollback

Before cutover, keep the previous application artifact and database backup. During the compatibility release, new schema is additive. If authentication fails broadly, disable registration/new-token issuance, restore the compatible app version and investigate without deleting new identity/membership records. Do not re-enable the hardcoded password initializer as a rollback mechanism.
