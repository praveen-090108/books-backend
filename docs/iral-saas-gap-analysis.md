# IRAL SaaS Gap Analysis

Date: 2026-10-23
Scope: existing `books-backend` and `books-frontend` repositories
Status: architecture audit only; no production authentication switch or tenant migration has been performed.

## Executive summary

IRAL is a Spring Boot 3.3 / Java 21 backend with React, React Router, Zustand and Axios on the frontend. It has database-backed users, BCrypt password hashes, signed JWT bearer authentication, method-level permission checks, Flyway migrations, S3-backed files, and substantial business functionality.

It is **not yet safe for multiple organizations**. Several newer tables contain `organization_id`, but application services and controllers use a constant value of `1`. The central `business_records` table, `app_users`, organization settings, branding, document numbering, lifecycle tables, resource history and multiple child tables do not carry tenant ownership. A registered second organization would therefore be able to collide with or potentially reach legacy data unless the backend data-access layer is refactored first.

## Existing system map

| Area | Current implementation | Classification | SaaS gap |
|---|---|---|---|
| Frontend | React, React Router, Zustand, Axios, IRAL landing/login and protected routes | Existing | No registration, verification, onboarding or platform-admin route tree |
| Backend | Spring Boot MVC, Spring Data JPA, JDBC in lead module, MySQL, Flyway | Existing | No tenant context or organization aggregate |
| Authentication | `app_users`, BCrypt, JWT bearer, forgot/reset endpoints | Partially Implemented | No email verification, username login, token/session revocation, rate limiting, organization status check or platform identity boundary |
| Login UI | Database login API | Partially Implemented | Prefilled `admin@intelliatech.com` / `admin123`; JWT stored in local storage |
| Bootstrap admin | Conditional `DefaultAdminInitializer` | Requires Refactoring | Hardcoded identity and password; sandbox enables it |
| Demo authentication | `DemoTokenAuthenticationFilter` remains in source | Requires Refactoring | Must be removed or test-profile-only and proven absent from production chain |
| Password reset | Random token hash and 30-minute expiry | Partially Implemented | Raw reset token is returned in API response; no email delivery, rate limiting, attempt tracking or session revocation |
| JWT | HMAC signed, 60-minute expiry | Partially Implemented | Subject is email only; no user ID, tenant/membership, platform role, token version or session ID |
| RBAC | Role name on `app_users`; role permissions stored as JSON in `business_records` | Partially Implemented | Roles are not first-class or tenant-owned; role name containing `admin` grants unrestricted access |
| Data scope | Creator/manager hierarchy through `DataScopeService` | Existing | It is not tenant isolation and currently treats administrators as globally unrestricted |
| Organization profile | `business_records` rows with settings types | Partially Implemented | No organization foreign key; “latest record” lookups are global |
| Branding | Settings record plus S3 logo | Partially Implemented | Global branding lookup; S3 keys do not include tenant ID |
| Users/employees | Employee resource then user creation | Existing | Users/resources are not tenant-owned; global email and resource uniqueness only |
| Sales/projects/resources/settings | Primarily stored in `business_records` | Requires Refactoring | Central table has no `organization_id`; record numbers are globally unique |
| Customers | Dedicated `customers` plus sales customer records | Requires Refactoring | Dedicated table has no tenant ID and email is globally unique |
| Vendors/purchases/expenses/assets/leads | Many tables already contain `organization_id` | Partially Implemented | Most services/controllers use `ORGANIZATION_ID = 1L`; related rows are not always independently scoped |
| Reports/dashboard/exports | Existing service queries and CSV/PDF endpoints | Requires Refactoring | Several aggregate queries are global or hardcoded to tenant 1 |
| E-invoice/IRP | `company_id` exists | Partially Implemented | Calls use company ID 1; environment and credentials must derive from authenticated tenant and document tenant |
| Files | S3 and compatibility local storage | Partially Implemented | Keys such as `branding/logo/...` are global; download authorization is not tied to tenant metadata |
| Audit | Module-specific activity/history tables | Partially Implemented | No platform audit log or organization status audit |
| Email | No Spring Mail dependency/provider abstraction found | Missing | Verification, invitation, reset and notification delivery needed |
| Subscription | No plan/subscription aggregate | Missing | Free plan and extensible entitlement model needed |
| Platform admin | None | Missing | Separate principal type, MFA, routes, metrics and audit required |
| Automated tenant security tests | None found | Missing | Cross-tenant detail/update/delete/export/file tests are mandatory |

## High-risk findings

1. `DefaultAdminInitializer` creates or updates `admin@intelliatech.com` using the literal password `admin123` when enabled.
2. `LoginPage` pre-populates those credentials.
3. `AuthService.forgot` returns the raw reset token to the client.
4. JWTs are persisted in browser local storage and cannot be centrally revoked.
5. JWT subject contains only the login email; tenant membership and organization status are resolved nowhere.
6. `CurrentUserService.isAdmin` treats any role name containing `admin` as an administrator, and `DataScopeService` makes that user unrestricted.
7. The core `business_records.record_number` constraint is global, which prevents independent tenant numbering and exposes collision risk.
8. Many “tenant-aware” modules still use constants such as `ORGANIZATION_ID = 1L` or `COMPANY_ID = 1L`.
9. Organization and branding retrieval uses the latest global settings record.
10. Branding uploads use a shared `branding/logo/` S3 prefix.
11. Lead management includes tenant columns but uses direct JDBC and a hardcoded tenant, so every query and relationship needs review.
12. Existing sessions would remain valid after a tenant suspension because neither JWT validation nor user lookup checks organization status/token version.

## Ownership classification

### Platform-owned reference data

These may remain global only after explicit confirmation: supply states, supported currencies/country catalogs, permission definitions, plan definitions and immutable product metadata. Organization overrides must use separate tenant-owned rows.

### Organization-owned roots

Organizations, organization settings, memberships, organization roles, resources/employees, customers, vendors, business records, leads/contacts/companies/pipelines, assets, projects, invoices, bills, expenses, purchase orders, payments, tax/IRP settings and numbering preferences.

### Organization-owned children

Invoice lifecycle/communications/payments/allocations, bill and purchase-order items/activities/payments, asset assignments/history/attachments/depreciation/maintenance/disposals, project milestones/teams/SOWs/documents/resource histories, lead activities/tasks/notes/attachments/shares and notifications. A child may derive ownership from its parent, but every repository access must join through the tenant-owned parent or carry a validated tenant key.

## Requirement status

- **Existing:** core modules, user/resource creation flow, basic JWT login, password hashing, role permissions, S3 upload, organization settings UI, landing page, dashboards and exports.
- **Partially Implemented:** database authentication, forgot/reset flow, organization-like settings, RBAC, audit histories, organization columns in newer modules, status fields, branding.
- **Missing:** public registration, email verification, organization/membership model, plan subscription, tenant context, platform super admin/MFA, platform UI/APIs, organization status enforcement, email service, auth rate limiting and tenant isolation tests.
- **Requires Refactoring:** bootstrap credentials, JWT/session model, core `business_records`, user/role uniqueness, global settings/branding lookup, S3 keys/download authorization, direct JDBC lead queries, reports/exports/background work, document numbering and every hardcoded tenant ID.

## Go/no-go conclusion

Public organization registration must not be enabled until organization context, legacy backfill, core-table tenant keys, scoped uniqueness constraints, repository guards and cross-tenant tests are complete. UI-only registration would be insecure. The safe implementation order is defined in `iral-saas-implementation-plan.md`.
