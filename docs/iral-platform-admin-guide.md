# IRAL Platform Administrator Guide

Date: 2026-10-23
Status: design guide; platform administration is not implemented yet.

## Security boundary

The IRAL Platform Administrator represents the product owner. It is not an Organization Admin role and must not be created through organization role management. Platform permissions never grant automatic access to customer invoices, payroll, attachments or detailed financial records.

## Secure bootstrap

1. Deployment operator enables bootstrap explicitly for one run.
2. Email and one-time activation secret are supplied through environment variables or an approved secrets store.
3. Backend creates one inactive platform identity idempotently and records an audit event.
4. Administrator activates the account, sets a strong password and enrolls MFA.
5. Bootstrap is disabled and the one-time secret is destroyed.

There is no default username/password. Bootstrap failure must fail closed and must never log secrets.

## Login and session

Use `/platform/login` and a distinct platform principal. Require MFA on every new platform session and step-up authentication for organization suspension/reactivation or security-setting changes. Sessions are server-tracked, short-lived, revocable and audited.

## Permitted capabilities

- view actual organization/user/registration counts and trends;
- search organizations and view registration/business metadata;
- view aggregate usage counts and storage totals when measurable;
- review verification and organization lifecycle state;
- suspend or reactivate an organization with a mandatory reason;
- view platform audit history and security events;
- manage platform settings and platform administrators subject to stronger controls.

## Prohibited by default

- opening tenant invoices, payroll, private documents or attachment contents;
- impersonating an organization user;
- changing organization business transactions;
- permanently deleting organizations or records;
- bypassing organization repositories using an unrestricted generic data service.

Future support access requires a separately approved, time-bound, purpose-bound, consent-aware and fully audited design.

## Organization status actions

Every transition records actor, reason, previous/new state, timestamp and correlation ID. Suspension/deactivation revokes or invalidates all organization sessions immediately. Reactivation does not silently restore deactivated users or expired credentials.

## Dashboard data

All cards and charts use database aggregates—never sample values. Queries are indexed and return metadata only. “Storage usage” is hidden or shown as unavailable until object metadata is reliable.

## Operational checks

- MFA and recovery method tested;
- no platform route visible to organization users;
- organization bearer/cookie receives 403 on all `/api/platform/**` endpoints;
- audit retention and access are configured;
- suspension revocation is tested;
- privacy review confirms no customer content is exposed;
- bootstrap variables are absent after provisioning.
