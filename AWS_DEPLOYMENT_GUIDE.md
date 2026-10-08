# IntelliaTech Books AWS deployment guide

This release upgrades the existing application and database in place. It does
not recreate the database, deploy automatically, or activate Production IRP.

## Observed deployment information and required confirmation

The application screenshots show the React frontend served from an AWS S3
website endpoint in `ap-south-1`. The repositories contain no CloudFormation,
Terraform, ECS, Elastic Beanstalk, CodeDeploy, systemd unit, bucket name,
CloudFront distribution ID, or backend host definition. Therefore the exact
backend AWS service and resource identifiers must be confirmed in the AWS
account before executing the parameterized commands below. Do not guess them.

## A. Before deployment

1. Confirm the frontend S3 bucket and whether CloudFront fronts it.
2. Confirm whether the backend is EC2, Elastic Beanstalk, ECS/Fargate, or another
   managed service; record its deployment/restart procedure and health URL.
3. Confirm the RDS MySQL endpoint, database name, maintenance window, backup
   retention, and the backend security group's access to port 3306.
4. Take a manual RDS snapshot and verify its status is `available`.
5. Preserve the currently deployed frontend build and backend artifact/version.
6. Generate a production encryption key on an approved secure administrator
   machine:

   ```bash
   openssl rand -base64 32
   ```

   Store it as `IRP_CREDENTIAL_ENCRYPTION_KEY` in the existing AWS runtime
   environment/secret facility. Do not put it in Git, a frontend variable, an
   AMI, or deployment logs. Every backend instance sharing this database must
   receive the same stable value.
7. If IRP credentials were saved locally with the public testing key, re-enter
   them through Settings after deployment. Changing the key cannot decrypt old
   ciphertext; do not silently rotate it.
8. Configure a strong `JWT_SECRET`, database credentials, CORS origin, and the
   S3 settings listed below. Prefer an EC2/ECS task role over static AWS keys.

## B. Database update

Flyway is already the project's migration framework. The required incremental
scripts and their order are documented in `deployment/database/README.md`.

1. Run `deployment/database/05_validation_queries.sql` read-only before release.
2. Verify Flyway versions 46-49 have not failed or been partially installed.
3. Start one new backend instance with migrations enabled. It will apply V46,
   V47, V48, and V49 in order and preserve all existing data.
4. Verify `flyway_schema_history` and rerun the validation queries before adding
   more backend instances.

Do not import `database/intelliatech_books_schema.sql` into production.

## C. Backend build and deployment

Required runtime: Java 21, Spring Boot 3.3.5, MySQL 8.

Build and test:

```bash
mvn clean test
mvn -DskipTests package
```

Artifact: `target/intelliatech-books-backend-0.0.1-SNAPSHOT.jar`.

Required runtime configuration:

```text
DB_URL=jdbc:mysql://<rds-endpoint>:3306/<database>?useSSL=true&serverTimezone=UTC
DB_USERNAME=<runtime-user>
DB_PASSWORD=<secret>
SERVER_ADDRESS=0.0.0.0
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=https://<production-frontend-host>
JWT_SECRET=<strong-stable-secret>
IRP_CREDENTIAL_ENCRYPTION_KEY=<stable-base64-32-byte-key>
AWS_S3_BUCKET=<existing-private-or-public-asset-bucket>
AWS_REGION=ap-south-1
AWS_S3_KEY_PREFIX=intelliatech-books
AWS_S3_PUBLIC_BASE_URL=<approved-public-or-cloudfront-base-url>
EINVOICE_PUBLIC_KEY_LOCATION=classpath:einvoice/EY_IRP_Sandbox_auth_public_key_2027.pem
```

If the confirmed backend is EC2/systemd, upload the versioned JAR to a new path,
update the service environment securely, and restart using the existing service
name. If it is Elastic Beanstalk, deploy the JAR as a new application version.
If it is ECS/Fargate, build/push the image using the existing Dockerfile/ECR
pipeline and create a new task-definition revision. No such service metadata is
present in this repository, so resource-specific commands must come from the
current AWS configuration.

Health check:

```bash
curl --fail https://<backend-host>/api/health
```

Do not place EY Client ID, Client Secret, API username, or API password in these
environment variables. They are entered in the administrator Settings UI and
stored encrypted in MySQL.

## D. Frontend build and deployment

Required runtime: Node.js 20+ and pnpm.

```bash
pnpm install --frozen-lockfile
VITE_API_BASE_URL=https://<backend-host>/api pnpm build
aws s3 sync dist/ s3://<frontend-bucket>/ --delete
```

Only use `--delete` after confirming the exact frontend bucket and reviewing a
dry run with `aws s3 sync dist/ s3://<frontend-bucket>/ --delete --dryrun`.
Configure S3/CloudFront SPA routing so unknown routes return `index.html`. If a
CloudFront distribution exists, invalidate after upload:

```bash
aws cloudfront create-invalidation --distribution-id <distribution-id> --paths '/*'
```

Use HTTPS through CloudFront or the existing approved frontend delivery layer.

## E. IRN / E-Invoice settings

1. Sign in as an administrator and open **Settings → IRN / E-Invoice Settings**.
2. Save Sandbox Client ID, Client Secret, API Username, API Password, Sandbox
   GSTIN, API version, and Sandbox base URL.
3. Run **Test Connection**, then explicitly activate Sandbox if appropriate.
4. Save Production credentials separately using the production GSTIN and
   `https://api.einvoice5.gst.gov.in/irp5` only after production access is
   approved.
5. Do not activate Production during deployment. Authentication testing must not
   generate a real IRN.
6. Cancellation/retrieval uses the environment recorded with each generated IRN.
   Review older IRNs whose environment is NULL instead of assigning one blindly.

## F. Post-deployment verification

Verify login, roles, dashboard, customers, invoices, credit notes, purchases,
expenses, projects, reports, existing settings, logo/asset S3 upload, and PDF
output. Then verify Sandbox configuration save/edit, masked credential behavior,
connection test, explicit environment activation, invoice IRN generation and
cancellation, Credit Note IRN generation and cancellation, QR/PDF display, and
invoice-to-credit-note linkage. Confirm Production remains inactive.

Check application logs for migration, authentication, encryption, or S3 errors,
but never log credentials, tokens, decrypted values, or the encryption key.

## G. Rollback plan

1. Stop new writes and scale the new backend down if health or migration checks
   fail.
2. Restore the pre-deployment RDS snapshot to a new RDS instance; do not perform
   destructive ad-hoc down migrations on the live instance.
3. Point the previous backend version at the restored database and its original
   stable encryption key/configuration.
4. Restore the saved frontend build to S3 and invalidate CloudFront if used.
5. Verify login and core read/write flows before reopening access.

The previous backend may not understand V46-V49 objects, but additive tables and
nullable columns are intentionally isolated. A database restore remains the
safest recovery whenever a MySQL DDL migration partially fails.

## Recommended release order

Review changes → run tests/builds → create release commits → push GitHub → take
RDS snapshot → validate/apply Flyway migrations → deploy and verify backend →
deploy frontend → run functional checks → test Sandbox authentication → leave
Production inactive until separately approved.
