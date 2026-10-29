# IRAL SaaS Implementation Plan

Date: 2026-10-23
Rule: no production auth switch, deployment or push without explicit approval.

## Phase 0 — Baseline and safety gates

- Preserve database backups and record baseline row counts per table.
- Add architecture decision records and a complete ownership inventory.
- Add test profiles using an isolated database/container.
- Make `DemoTokenAuthenticationFilter` test-only or remove it.
- Remove credential prefilling from the login UI.
- Disable the hardcoded bootstrap admin in every normal profile; replace it only after secure migration/bootstrap paths exist.

Exit gate: current application builds/tests, legacy admin login is recoverable through an approved migration procedure, and no production credential is in Git.

## Phase 1 — Foundation schema

Introduce additive Flyway migrations only:

1. `organizations`, `organization_settings` and organization status history.
2. `plans` and `organization_subscriptions`; seed `FREE` idempotently.
3. global identity security columns and `organization_memberships`.
4. tenant roles/role-permission mappings while preserving existing permission codes.
5. verification/reset/session tables and platform audit logs.
6. secure platform-admin identity/MFA tables.

Create the legacy organization using an explicit deployment parameter or reviewed migration value. Do not guess the legal identity.

Exit gate: schema is additive and rollback instructions are verified.

## Phase 2 — Tenant context and secure authentication

- Introduce an authenticated principal and `TenantContext`.
- Replace email-only JWT claims with immutable IDs, membership, session ID and security versions.
- Add organization/user/membership status enforcement to every request.
- Add registration, verification/resend, login/logout, forgot/reset and session revocation.
- Add an email provider abstraction and transactional outbox.
- Add rate limiting and generic authentication errors.
- Support BCrypt-to-Argon2id progressive rehash.
- Add secure platform bootstrap through environment variables, one-time activation and mandatory MFA.

Exit gate: registration and auth security integration tests pass, but public registration remains feature-flagged off.

## Phase 3 — Core ownership migration

Prioritize the central/high-blast-radius tables:

- `business_records` and its resource/project/invoice relationships;
- `app_users` through memberships;
- customers and organization settings/branding;
- document number preferences/counters;
- invoice lifecycle, payments, communications, reminders, allocations and e-invoice details;
- resource lifecycle/history and project allocations/documents/milestones/teams/SOW history.

Add nullable tenant columns, backfill, validate, add composite uniqueness/indexes, update code to tenant-qualified access, then make columns non-null. Global unique constraints must not be dropped before equivalent tenant composite constraints exist.

Exit gate: Organization A/B cross-tenant CRUD, report, export and nested-resource tests pass for migrated modules.

## Phase 4 — Existing partially scoped modules

Replace every `ORGANIZATION_ID = 1L`, `COMPANY_ID = 1L` and SQL literal with `TenantContext` in:

- vendors, bills, expenses and purchase orders;
- assets and their child records;
- leads, contacts, companies, pipelines, dashboards and reports;
- masters, bank accounts, dashboard metrics and IRP/e-invoice services.

Validate relationship ownership (vendor-to-bill, company-to-contact, asset-to-assignment, project-to-resource, IRP document-to-environment). Add tenant keys to child tables when parent joins are insufficient or unsafe.

Exit gate: no hardcoded tenant IDs remain in runtime code and a static check prevents reintroduction.

## Phase 5 — Tenant settings, files and branding

- Move global settings rows to `organization_settings`.
- Prefix new S3 objects by organization and store authorization metadata.
- Tenant-qualify upload/download/delete and public branding retrieval.
- Update invoice/PDF generation to load organization-scoped profile, bank, IRP and branding.
- Add migration tooling for legacy object keys without breaking historical downloads.

Exit gate: two organizations can upload same-named logos and documents without visibility crossover.

## Phase 6 — Platform administration

- Add `/api/platform/**` services for metadata aggregates, organization listing/details, usage and status transitions.
- Add `/platform/login`, mandatory MFA, platform dashboard/navigation and organization management.
- Keep platform repositories metadata-only; no ordinary tenant bypass.
- Revoke organization sessions on suspension/deactivation and audit every action.

Exit gate: ordinary organization tokens receive 403 for every platform endpoint; platform screens do not expose confidential business rows.

## Phase 7 — UI and onboarding

- Change public CTA wording to **Start Free** and route to `/register`.
- Add responsive registration, verification, resend, login, forgot/reset and status pages.
- Add optional onboarding checklist backed by actual provisioning state.
- Add organization profile/branding and tenant-aware user/role administration.
- Separate platform route tree and layout.

Exit gate: accessible browser-level registration-to-dashboard flow passes on supported screen sizes.

## Phase 8 — Production readiness

- Run full backend tests, frontend build, migration rehearsal, security tests and dependency scanning.
- Run tenant isolation tests across CRUD, nested IDs, exports, files, reports and background jobs.
- Validate backups, rollback and observability.
- Enable registration only after staged verification and product-owner approval.

## Migration and rollback principles

- Expand/contract migrations; never destructive in the same release as backfill.
- Database backup and tested restore precede migration.
- Each backfill records counts, nulls, orphans and duplicate conflicts.
- Application feature flags allow old login and public registration to be controlled independently during validation.
- Rollback disables new traffic and restores the compatible application version; data columns/tables remain until a separately approved cleanup release.

## Initial implementation backlog

1. Remove UI credential defaults and return-token reset behavior.
2. Add organization/plan/membership/security schema migration.
3. Add organization entities and repositories.
4. Add tenant principal/context and organization-status filter.
5. Add registration/provisioning/verification backend with tests.
6. Add registration and verification frontend.
7. Migrate `business_records` ownership and scoped uniqueness.
8. Refactor settings, branding and file keys.
9. Eliminate hardcoded organization ID usages module by module.
10. Add platform identity/MFA and administration after tenant isolation is proven.
