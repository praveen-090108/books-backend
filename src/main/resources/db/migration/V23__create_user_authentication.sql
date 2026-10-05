CREATE TABLE app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    legacy_record_id BIGINT NULL,
    name VARCHAR(160) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NULL,
    phone VARCHAR(32) NULL,
    designation VARCHAR(120) NULL,
    role_name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Active',
    module_access TEXT NULL,
    reset_token_hash VARCHAR(64) NULL,
    reset_token_expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT uk_app_users_legacy_record UNIQUE (legacy_record_id),
    CONSTRAINT fk_app_users_legacy_record FOREIGN KEY (legacy_record_id) REFERENCES business_records(id) ON DELETE SET NULL
);

CREATE INDEX idx_app_users_role_status ON app_users(role_name, status);
CREATE INDEX idx_app_users_reset_token ON app_users(reset_token_hash);

INSERT INTO app_users (legacy_record_id, name, email, phone, designation, role_name, status, module_access)
SELECT id, party_name, LOWER(party_email), party_phone, reference_number,
       COALESCE(NULLIF(category, ''), 'User'), status, secondary_status
FROM business_records
WHERE module = 'settings' AND type = 'users' AND party_email IS NOT NULL
ON DUPLICATE KEY UPDATE legacy_record_id = VALUES(legacy_record_id);
