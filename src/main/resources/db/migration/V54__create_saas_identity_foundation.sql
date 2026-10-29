CREATE TABLE plans (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    price DECIMAL(14,2) NOT NULL DEFAULT 0,
    billing_cycle VARCHAR(24) NOT NULL DEFAULT 'NONE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    user_limit INT NULL,
    storage_limit_bytes BIGINT NULL,
    enabled_features_json TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_plans_code UNIQUE (code)
);

INSERT INTO plans(code,name,price,billing_cycle,active)
VALUES ('FREE','Free',0,'NONE',TRUE)
ON DUPLICATE KEY UPDATE name=VALUES(name), price=VALUES(price), billing_cycle=VALUES(billing_cycle), active=TRUE;

CREATE TABLE organizations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    organization_name VARCHAR(180) NOT NULL,
    legal_name VARCHAR(180) NULL,
    gstin VARCHAR(15) NULL,
    pan VARCHAR(10) NULL,
    country_code CHAR(2) NOT NULL DEFAULT 'IN',
    status VARCHAR(32) NOT NULL,
    security_version BIGINT NOT NULL DEFAULT 0,
    verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_organizations_public_id UNIQUE (public_id),
    CONSTRAINT uk_organizations_slug UNIQUE (slug),
    CONSTRAINT uk_organizations_gstin UNIQUE (gstin),
    INDEX idx_organizations_status_created (status, created_at)
);

INSERT INTO organizations(id,public_id,slug,organization_name,legal_name,country_code,status,verified_at)
SELECT 1,UUID(),'legacy-organization',
       COALESCE((SELECT party_name FROM business_records WHERE module='settings' AND type='organization' ORDER BY record_date DESC,id DESC LIMIT 1),'Legacy IRAL Organization'),
       (SELECT party_name FROM business_records WHERE module='settings' AND type='organization' ORDER BY record_date DESC,id DESC LIMIT 1),
       'IN','ACTIVE',NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM organizations WHERE id=1);

CREATE TABLE organization_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    logo_key VARCHAR(500) NULL,
    address_line1 VARCHAR(255) NULL,
    address_line2 VARCHAR(255) NULL,
    city VARCHAR(100) NULL,
    state_name VARCHAR(100) NULL,
    postal_code VARCHAR(20) NULL,
    contact_email VARCHAR(180) NULL,
    phone VARCHAR(32) NULL,
    website VARCHAR(255) NULL,
    base_currency CHAR(3) NOT NULL DEFAULT 'INR',
    financial_year_start_month TINYINT NOT NULL DEFAULT 4,
    onboarding_json TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_organization_settings_org UNIQUE (organization_id),
    CONSTRAINT fk_organization_settings_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

INSERT IGNORE INTO organization_settings(organization_id,contact_email,phone)
SELECT 1,party_email,party_phone FROM business_records
WHERE module='settings' AND type='organization' ORDER BY record_date DESC,id DESC LIMIT 1;
INSERT IGNORE INTO organization_settings(organization_id) VALUES (1);

CREATE TABLE organization_roles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    permissions_json TEXT NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_organization_roles_code UNIQUE (organization_id,code),
    CONSTRAINT fk_organization_roles_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

INSERT INTO organization_roles(organization_id,code,name,permissions_json,system_role)
VALUES (1,'ORGANIZATION_ADMIN','Organization Admin','["*"]',TRUE),
       (1,'LEGACY_USER','Legacy User','[]',TRUE)
ON DUPLICATE KEY UPDATE name=VALUES(name);

ALTER TABLE app_users
    ADD COLUMN username VARCHAR(80) NULL AFTER email,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER password_hash,
    ADD COLUMN account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER email_verified,
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 AFTER account_status,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0 AFTER token_version,
    ADD COLUMN locked_until DATETIME(6) NULL AFTER failed_login_attempts,
    ADD COLUMN last_login_at DATETIME(6) NULL AFTER locked_until;

UPDATE app_users SET email_verified=TRUE, account_status='ACTIVE' WHERE id IS NOT NULL;
CREATE UNIQUE INDEX uk_app_users_username ON app_users(username);

CREATE TABLE organization_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    joined_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_organization_membership UNIQUE (organization_id,user_id),
    CONSTRAINT fk_membership_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_role FOREIGN KEY (role_id) REFERENCES organization_roles(id) ON DELETE RESTRICT,
    INDEX idx_membership_user_status (user_id,status),
    INDEX idx_membership_org_status (organization_id,status)
);

INSERT INTO organization_memberships(organization_id,user_id,role_id,status,joined_at)
SELECT 1,u.id,r.id,'ACTIVE',COALESCE(u.created_at,NOW(6))
FROM app_users u
JOIN organization_roles r ON r.organization_id=1
 AND r.code=CASE WHEN LOWER(u.role_name) LIKE '%admin%' THEN 'ORGANIZATION_ADMIN' ELSE 'LEGACY_USER' END
ON DUPLICATE KEY UPDATE status='ACTIVE';

CREATE TABLE organization_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_organization_subscription_org UNIQUE (organization_id),
    CONSTRAINT fk_subscription_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_plan FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT
);

INSERT IGNORE INTO organization_subscriptions(organization_id,plan_id,status,started_at)
SELECT 1,id,'ACTIVE',NOW(6) FROM plans WHERE code='FREE';

CREATE TABLE email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_email_verification_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_email_verification_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    INDEX idx_email_verification_active (user_id,consumed_at,expires_at)
);

CREATE TABLE platform_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NULL,
    organization_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    metadata_json TEXT NULL,
    correlation_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_platform_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_platform_audit_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE SET NULL,
    INDEX idx_platform_audit_org_time (organization_id,created_at),
    INDEX idx_platform_audit_actor_time (actor_user_id,created_at)
);

CREATE TABLE auth_rate_limits (
    bucket_key CHAR(64) NOT NULL PRIMARY KEY,
    attempt_count INT NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    INDEX idx_auth_rate_limits_expiry (expires_at)
);
