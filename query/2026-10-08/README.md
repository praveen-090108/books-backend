# Database update package — 2026-10-08

This folder is the date-wise deployment package for the IntelliaTech Books
E-Invoice and IRP Settings release. It updates the existing MySQL database in
place and must not be used to recreate or replace the production database.

## Files and execution order

1. `V46__create_e_invoice_integration.sql`
2. `V47__extend_e_invoice_irn_details.sql`
3. `V48__support_credit_note_e_invoice_irn.sql`
4. `V49__create_irp_configuration.sql`
5. `05_validation_queries.sql` — read-only verification after migration

The same V46-V49 files remain in `src/main/resources/db/migration/` because
Flyway is the application's authoritative migration mechanism. Copies are kept
here only to provide a convenient, date-wise deployment package.

## Recommended production procedure

1. Confirm the target is the existing IntelliaTech Books production database.
2. Take an RDS snapshot and wait until its status is `available`.
3. Run the read-only queries in `05_validation_queries.sql` and record the
   current Flyway state.
4. Stop or drain application instances that can perform schema changes.
5. Deploy one backend instance with Flyway enabled. It will execute pending
   migrations in version order under Flyway's schema-history lock.
6. Do not manually execute these files and then also allow Flyway to execute
   them. If manual execution is mandatory, an approved Flyway baseline/repair
   procedure must update `flyway_schema_history` consistently.
7. Run `05_validation_queries.sql` again and verify all expected tables,
   columns, indexes, constraints, and successful Flyway versions.
8. Deploy remaining backend instances, verify `/api/health`, and then deploy the
   frontend.

## Data-safety rules

- Do not run a full schema dump against production.
- Do not drop, truncate, reset, or recreate existing tables.
- Do not overwrite historical invoice or Credit Note IRNs.
- Do not assign an environment to old IRNs unless their origin is verified.
- Do not insert test credentials, GSTINs, customers, invoices, or Credit Notes.
- Do not activate Production IRP as part of database deployment.

## Migration contents

- V46 creates `e_invoice_details` and `e_invoice_api_logs`.
- V47 adds E-Way Bill response and remarks fields.
- V48 adds Credit Note IRN ownership while preserving Invoice IRN records.
- V49 adds encrypted Sandbox/Production IRP configuration, audit history, and
  nullable provider/environment/GSTIN provenance fields on IRN records.

## Recovery

MySQL DDL may auto-commit. If a migration fails, stop application writes,
preserve the Flyway and backend logs, and restore the verified pre-deployment
RDS snapshot to a new database instance. Point the previous backend release to
the restored database. Do not attempt destructive ad-hoc rollback queries on
the live production database.

See `../../AWS_DEPLOYMENT_GUIDE.md` for the complete AWS deployment checklist,
runtime environment variables, frontend/backend steps, verification, and
rollback process.
