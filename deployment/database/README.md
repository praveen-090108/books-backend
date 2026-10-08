# Production database update (Flyway V46-V49)

The production database must be upgraded in place. Do not import
`database/intelliatech_books_schema.sql`; that file is only a schema snapshot.

## Authoritative executable migrations

Apply these files in this exact order through the backend's existing Flyway
configuration:

1. `src/main/resources/db/migration/V46__create_e_invoice_integration.sql`
2. `src/main/resources/db/migration/V47__extend_e_invoice_irn_details.sql`
3. `src/main/resources/db/migration/V48__support_credit_note_e_invoice_irn.sql`
4. `src/main/resources/db/migration/V49__create_irp_configuration.sql`

V46 creates e-invoice detail and API audit tables. V47 adds E-Way Bill response
fields. V48 adds Credit Note IRN ownership without changing existing invoice
IRNs. V49 adds encrypted Sandbox/Production IRP configuration, configuration
audit history, and nullable provider/environment/GSTIN provenance fields on
historical IRN rows.

No sample credentials, GSTINs, invoices, customers, or other business data are
inserted. No table is dropped or truncated. No existing IRN is assigned an
environment automatically.

## Safe execution

1. Take and verify an RDS snapshot/backup.
2. Record the deployed application version and current Flyway version.
3. Run `05_validation_queries.sql` before deployment. Versions 46-49 should be
   absent (or already successful after a prior deployment).
4. Deploy one backend instance with `SPRING_FLYWAY_ENABLED=true`. Flyway obtains
   its schema-history lock and applies only pending scripts.
5. Do not run the SQL files manually and then also let Flyway run them. If the
   organization requires manual execution, use an approved Flyway baseline or
   repair procedure so `flyway_schema_history` remains authoritative.
6. Run `05_validation_queries.sql` again and verify every expected object.

## Recovery

MySQL DDL can auto-commit, so these schema migrations do not have a reliable
single-transaction down migration. If migration fails, stop application writes,
retain the failure logs, and restore the verified pre-deployment RDS snapshot to
a new instance. Point the previous backend version at the restored instance.
Never improvise `DROP`, `TRUNCATE`, or data deletion against the live database.
