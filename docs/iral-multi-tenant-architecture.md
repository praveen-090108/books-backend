# IRAL Multi-Tenant Architecture

Date: 2026-10-23

## Decision

Use a shared application and shared MySQL schema with strict row-level organization ownership. Use a global identity plus organization membership model. Initially, a normal user may have one active organization membership; the schema remains capable of supporting multiple memberships later.

Platform administrators are global identities with a separate platform role and MFA state. They are not organization members and never inherit organization data access merely from their platform role.

## Core model

### Global identity

`app_users` remains the login identity and stores immutable identity/security state: normalized email, optional normalized username, password hash, verification/account state, token version, failed-attempt counters and timestamps. Employee profile data must not be required to authenticate.

### Organization

Create `organizations` with a stable public identifier, name/legal name, normalized GSTIN/PAN, country, lifecycle status, verification timestamps and audit timestamps.

Create one `organization_settings` row per organization. Store profile, address, tax, currency, financial year and tenant branding metadata there rather than selecting the latest global settings record.

### Membership and RBAC

Create `organization_memberships(organization_id, user_id, status, role_id, joined_at, ...)` with a unique organization/user constraint. Convert roles into tenant-owned records, or introduce `organization_roles` while retaining the current permission codes. Permission definitions remain platform-owned; role-permission assignments are tenant-owned.

Enforce the invariant that every active organization has at least one active Organization Admin. Removing or deactivating the final administrator must fail transactionally.

### Plans

Create platform-owned `plans` and tenant-owned `organization_subscriptions`. Seed one active `FREE` plan at zero price with no expiry. Entitlement evaluation must be centralized and must not imply limits until approved.

### Platform administration

Create platform-role/MFA data separately from organization membership. Platform APIs use `/api/platform/**`, a platform authority and explicit metadata-only services. No general bypass of organization repositories is allowed.

## Tenant context

Authentication resolves a principal containing:

- global user ID;
- identity type (`ORGANIZATION_USER` or `PLATFORM_ADMIN`);
- active membership ID and organization ID for organization users;
- role ID and permission version;
- token/session ID and token version.

`TenantContext` is created server-side from the verified token/session plus a current database membership/status lookup. Controllers never accept organization ID as an authorization input. Services obtain it from `TenantContext.requireOrganizationId()`.

An organization request fails when the membership, user or organization is not active. Suspension increments an organization security version or revokes its sessions so existing tokens stop working immediately.

## Data-access enforcement

1. Every organization aggregate root has non-null `organization_id` and an index beginning with it.
2. Unique business identifiers become tenant composite constraints, for example `(organization_id, record_number)`.
3. Repositories expose tenant-qualified methods such as `findByIdAndOrganizationId` rather than unrestricted `findById` for organization entities.
4. Related IDs supplied by clients are loaded using the current organization before association.
5. Direct JDBC queries bind the tenant ID as their first access predicate.
6. Reports, counts, exports and background jobs accept an internal trusted tenant context, never a client tenant ID.
7. Child-table access either carries `organization_id` or joins through a tenant-qualified parent. The choice is documented per table.
8. Native/global repository methods are restricted to migration or platform metadata services and covered by tests.

The existing creator/manager data scope is applied **inside** the tenant boundary:

`organization predicate AND role permission AND creator/manager scope`.

Administrators may be unrestricted only within their organization.

## File isolation

Store file metadata with organization ID, owner entity type/ID, storage key, content type, size, checksum and lifecycle state. New S3 keys use:

`organizations/{organizationId}/{category}/{entityId}/{uuid}`

Downloads resolve metadata by current tenant before issuing bytes or a short-lived URL. A raw key or public URL is never sufficient authorization. Branding lookup uses `organization_settings.logo_key`.

## Authentication design

- Prefer Argon2id for new password hashes; support BCrypt verification during migration and rehash on successful login.
- Deliver verification and reset tokens only by email. Persist only a keyed hash, purpose, expiry, consumed timestamp and attempt metadata.
- Use short-lived access tokens in Secure, HttpOnly, SameSite cookies plus rotating server-tracked refresh sessions where deployment topology permits. If bearer tokens are retained temporarily, add token/session IDs, revocation and strict CSP/XSS mitigation.
- Rate-limit registration, login, resend, forgot/reset and platform login by identity and network signals.
- Platform admins require MFA and a separate login route/context.
- Record security events without credentials, tokens, PAN or unmasked sensitive payloads.

## Registration transaction

1. Validate and normalize the request.
2. Enforce identity and tax-identifier uniqueness rules.
3. Create a `PENDING_VERIFICATION` organization.
4. Create/reuse an unverified global identity.
5. Create a pending Organization Admin membership and role assignment.
6. Create a single-use verification challenge and enqueue email after transaction commit.
7. On verification, lock the challenge and organization, consume once, activate identity/membership/organization, create settings, masters and free subscription idempotently.

Provisioning uses a unique organization ID and `INSERT ... ON DUPLICATE KEY` or equivalent idempotent services. Failure leaves the organization pending and retryable.

## Legacy migration

Create one explicitly approved legacy organization. Backfill all existing organization-owned roots and children to it before making columns non-null. Link current users through memberships. Migrate existing organization/branding settings into `organization_settings`. Do not make the legacy Organization Admin a Platform Super Admin automatically.

Run orphan, count and cross-reference validation before and after each migration. Keep old columns/read paths during a compatibility window, then remove hardcoded tenant constants only after application code is dual-read/tenant-aware and tests pass.

## Status and privacy enforcement

Organization states: `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED`. Only `ACTIVE` may use protected organization APIs. Platform organization screens expose metadata and aggregate usage, not invoice line items, payroll, attachments or private financial content.

All status transitions require a reason and append a platform audit event with actor, previous/new status, timestamp and correlation ID.
