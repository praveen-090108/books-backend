# EY IRP 5 sandbox qualification runner

This runner is isolated from application invoices and credit notes. It writes only to the
`ey_qualification_*` tables created by Flyway migration V52 and calls the existing EY transport client.

## Safety defaults

External execution is disabled by default. The runner refuses to start unless all of these checks pass:

- `app.environment` is `sandbox`.
- The active database IRP configuration is `SANDBOX`.
- The configured endpoint host is on the explicit sandbox allowlist.
- Both qualification enablement flags are true.
- The stored sandbox configuration can be decrypted and is complete.

It never falls back to production. The invalid-client authentication scenario is disabled by default to
avoid account lockout; it must not be enabled without written provider approval.

## Controlled CLI execution

After provider permission and the active sandbox configuration have been independently verified:

```bash
EY_QUALIFICATION_ENABLED=true \
EY_QUALIFICATION_EXECUTION_PERMITTED=true \
EY_QUALIFICATION_RUN_ON_STARTUP=true \
EY_QUALIFICATION_SUITE=AUTHENTICATE \
EY_QUALIFICATION_MAX_OPERATIONS=1 \
java -jar target/intelliatech-books-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=sandbox
```

Suites are `AUTHENTICATE`, `GENERATE_IRN`, `GET_IRN_DETAILS`, `CANCEL_IRN`, and `ALL`.
Start with one operation. Increase the cap only after checking the provider response and applicable rate limits.

## Administrator endpoints

- `POST /api/admin/einvoice/qualification/runs?suite=AUTHENTICATE`
- `POST /api/admin/einvoice/qualification/runs/scenario/{scenarioId}`
- `POST /api/admin/einvoice/qualification/runs/{runId}/stop`
- `GET /api/admin/einvoice/qualification/runs/{runId}/progress`
- `PUT /api/admin/einvoice/qualification/portal-progress/{api}`

These endpoints require the Admin role. Portal counts are entered separately and remain authoritative.
Local expected failures are never promoted to verified portal counts.

## Reports

Each completed run writes JSON, XLSX, and PDF files under `reports/ey-sandbox-qualification/` by default.
Credentials, access tokens, encryption keys, and full sensitive payloads are excluded.

## Current provider verification status

No authorized eligibility-count endpoint is configured in this application. Update official portal counts
manually through the administrator endpoint after checking the provider portal. Do not claim production
eligibility from local execution counts.
