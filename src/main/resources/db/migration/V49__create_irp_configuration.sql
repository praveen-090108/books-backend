CREATE TABLE irp_configuration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL DEFAULT 1,
    provider VARCHAR(40) NOT NULL DEFAULT 'EY_IRP_5',
    environment VARCHAR(20) NOT NULL,
    api_base_url VARCHAR(500) NOT NULL,
    client_id_encrypted TEXT NOT NULL,
    client_secret_encrypted TEXT NOT NULL,
    api_username_encrypted TEXT NOT NULL,
    api_password_encrypted TEXT NOT NULL,
    gstin VARCHAR(15) NOT NULL,
    api_version VARCHAR(20) NULL,
    is_active BIT(1) NOT NULL DEFAULT b'0',
    is_configured BIT(1) NOT NULL DEFAULT b'1',
    credential_version BIGINT NOT NULL DEFAULT 1,
    last_connection_test DATETIME(6) NULL,
    last_connection_status VARCHAR(20) NULL,
    created_by VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_irp_configuration_company_provider_env UNIQUE (company_id, provider, environment),
    INDEX idx_irp_configuration_active (company_id, is_active)
);

CREATE TABLE irp_configuration_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    configuration_id BIGINT NULL,
    company_id BIGINT NOT NULL,
    provider VARCHAR(40) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    action VARCHAR(40) NOT NULL,
    performed_by VARCHAR(255) NULL,
    performed_at DATETIME(6) NOT NULL,
    details VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    INDEX idx_irp_config_audit_company_time (company_id, performed_at)
);

ALTER TABLE e_invoice_details
    ADD COLUMN irp_provider VARCHAR(40) NULL AFTER credit_note_id,
    ADD COLUMN irp_environment VARCHAR(20) NULL AFTER irp_provider,
    ADD COLUMN irp_gstin VARCHAR(15) NULL AFTER irp_environment;
