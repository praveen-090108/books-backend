# IntelliaTech Books database

`intelliatech_books_schema.sql` is a schema-only snapshot of the current local MySQL database. It intentionally excludes live user, customer, vendor, employee, authentication, and transaction rows because this repository is public.

The authoritative, versioned database history—including reference/master seed data—is in `src/main/resources/db/migration` and is applied by Flyway when the Spring Boot application starts.

For a new database, configure the datasource environment variables and start the backend. Flyway will build the schema in migration order. The schema snapshot is provided for review and manual provisioning.
