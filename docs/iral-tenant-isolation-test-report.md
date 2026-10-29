# IRAL Tenant Isolation Implementation and Test Report

Date: 2026-10-27
Status: **Core isolation implemented and locally verified; full three-tenant API penetration matrix remains a release gate**

## Root cause

Aartek could see IntelliaTech data because the SaaS identity tables were added after the original single-company business schema. The authenticated membership was not carried as an enforced persistence tenant, central records (`business_records`, `customers`, and `document_number_preferences`) had no tenant column, multiple services/controllers used organization/company ID `1`, and the frontend retained shared React Query data while changing sessions.

## Controls implemented

- JWTs carry the authorized organization and every protected request revalidates the active user membership.
- `TenantContextHolder` binds the organization to the current request thread and is always cleared in the authentication filter's `finally` block.
- Missing tenant context fails closed; there is no fallback to organization `1`.
- Hibernate `@TenantId` enforcement covers all existing organization-owned entities with an `organizationId` or `companyId` field.
- Explicit tenant repository predicates protect core list/detail/create/update/delete flows and dashboard aggregates.
- Hardcoded tenant `1` was removed from runtime Java services/controllers, including sales/purchases, projects/resources, assets, leads, bank/settings, and EY IRP configuration/authentication.
- Login response includes organization identity. The frontend clears the entire React Query cache before establishing or clearing a session, preventing previous-tenant flashes.
- Product platform administration remains separate from organization membership authorization.

## Migration V57

`V57__enforce_core_tenant_ownership.sql` adds mandatory tenant ownership to `business_records`, `customers`, and `document_number_preferences`. It also adds foreign keys, lookup indexes, tenant-scoped unique constraints, and `tenant_data_ownership_audit`.

V54 explicitly created organization `1` as the owner of the pre-SaaS dataset. V57 uses that recorded migration evidence for legacy rows only and writes one audit row per assignment. It is not a runtime fallback or a general rule for future data.

### Local migration result

| Check | Result |
|---|---:|
| Flyway schema version | 57, successful |
| Existing organizations | 3 |
| Preserved business records | 95 |
| Preserved document-number preferences | 8 |
| Ownership audit rows | 103 |
| Null tenant IDs after migration | 0 |
| Business-record ownership | 95 rows assigned to verified legacy organization 1 |
| Preference ownership | 8 rows assigned to verified legacy organization 1 |
| Customers | 0 existing rows |

The original row counts were unchanged. No table was deleted, truncated, or reset.

## Organization profile, users, and payments hardening (V58)

- Organization Profile now reads and updates the authenticated organization's `organizations` and `organization_settings` rows instead of the legacy shared settings record. Newly registered organizations therefore begin with only their registration data and may complete the remaining fields later.
- The Settings user list is built from the authenticated organization's memberships. Membership roles are rendered per organization, so another organization's users or role labels are not returned.
- `V58__tenant_scope_invoice_payments.sql` adds mandatory `organization_id` ownership to received payments, replaces global payment-number/idempotency uniqueness with tenant-scoped constraints, and records the legacy assignment audit.
- Payments Received lists, payment lookup paths, and overdue-invoice summary calculations are tenant-scoped.

### Local V58 migration result

| Check | Result |
|---|---:|
| Flyway schema version | 58, successful |
| Existing received payments | 8 |
| Payments assigned to verified legacy organization 1 | 8 |
| V58 ownership audit rows | 8 |
| Null payment tenant IDs after migration | 0 |
| Aartek active memberships | 1 |
| Aartek inherited payments | 0 |

## Modules reviewed and protected

- Authentication, registration, membership validation, JWT, and organization status
- Dashboard and organization profile/settings
- Customers and central business records: quotations, invoices, credit notes, orders, projects, resources, roles, and branding
- Vendors, bills, purchase orders, expenses, number preferences, bank accounts, tax/category/domain masters
- Staffing/fixed-cost project and resource lifecycle queries backed by tenant-scoped business records
- Asset master, categories, assignment, history, maintenance, depreciation, and disposal
- Leads, contacts, companies, pipelines, activities, notifications, reports, and exports
- IRP configuration, authentication, and API calls
- S3/logo key construction and integration settings

## Verification results

| Verification | Result |
|---|---|
| Backend `mvn clean test` | **PASS — 81 tests, 0 failures, 0 errors, 1 gated EY live test skipped** |
| Concurrent tenant context test | **PASS — organizations 101, 202, and 303 isolated across three request threads** |
| Hardcoded runtime tenant-ID scan | **PASS — no organization/company ID 1 assignments in `src/main/java`** |
| Frontend production build | **PASS — 1,769 modules transformed** |
| Flyway validation/startup | **PASS — 57 migrations validated, schema current at V58** |
| Backend health | **PASS — application and database UP** |
| Tenant-scoped unique indexes | **PASS — record number, customer email, and document type** |

The EY live authentication test is intentionally disabled unless `EINVOICE_LIVE_TEST=true`; no EY qualification traffic was sent during this security change.

## Remaining release risks and required follow-up

- The automated suite verifies tenant context behavior and affected service regressions, but it does not yet execute every master-prompt scenario through HTTP for three fully populated organizations. Before production, add Testcontainers/MySQL integration tests for cross-tenant ID swapping, updates/deletes, reports/exports, and relationships.
- Child tables whose ownership is inherited only through a tenant-owned parent should receive explicit organization columns and composite foreign keys in a future hardening migration where the schema permits it.
- Private S3 downloads still require endpoint-by-endpoint authorization tests with real object fixtures; do not make tenant documents public.
- Scheduled/background tasks must establish and clear an explicit tenant context for each organization before processing. New jobs must never inherit request ThreadLocal state.
- Flyway 10.10 reports MySQL 8.4 as newer than its tested range (8.1). Upgrade Flyway before production certification.
- The frontend bundle is valid but reports a large-chunk warning; this is a performance concern, not an isolation failure.

## Deployment safety

1. Take an application-consistent MySQL backup before V57/V58.
2. Stop application writers, deploy the backend, and allow Flyway to apply V57 and V58 once.
3. Verify `tenant_data_ownership_audit`, null counts, foreign keys, and tenant-scoped indexes.
4. Run the three-organization API security matrix in staging.
5. Roll back application binaries only with a database restore plan; V57/V58 contain MySQL DDL and should not be reversed ad hoc.

No GitHub push or deployment was performed.
