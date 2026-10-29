-- Core tenant ownership migration.
-- V54 explicitly created organization 1 as the owner of the pre-SaaS dataset.
-- The assignments below therefore preserve that known legacy ownership; they
-- must not be copied as a generic rule for future nullable tenant columns.

CREATE TABLE tenant_data_ownership_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    record_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    assignment_reason VARCHAR(255) NOT NULL,
    migration_version VARCHAR(32) NOT NULL,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_tenant_ownership_audit UNIQUE (table_name, record_id, migration_version),
    CONSTRAINT fk_tenant_ownership_audit_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    INDEX idx_tenant_ownership_audit_org_table (organization_id, table_name)
);

ALTER TABLE business_records ADD COLUMN organization_id BIGINT NULL AFTER id;
INSERT INTO tenant_data_ownership_audit(table_name,record_id,organization_id,assignment_reason,migration_version)
SELECT 'business_records',id,1,'Known pre-SaaS rows assigned to the legacy organization created by V54','V57'
FROM business_records WHERE organization_id IS NULL;
UPDATE business_records SET organization_id=1 WHERE organization_id IS NULL;
ALTER TABLE business_records
    MODIFY organization_id BIGINT NOT NULL,
    DROP INDEX uk_business_records_record_number,
    ADD CONSTRAINT fk_business_records_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_business_records_org_number UNIQUE (organization_id,record_number),
    ADD INDEX idx_business_records_org_module_type (organization_id,module,type),
    ADD INDEX idx_business_records_org_record_date (organization_id,record_date);

ALTER TABLE customers ADD COLUMN organization_id BIGINT NULL AFTER id;
INSERT INTO tenant_data_ownership_audit(table_name,record_id,organization_id,assignment_reason,migration_version)
SELECT 'customers',id,1,'Known pre-SaaS rows assigned to the legacy organization created by V54','V57'
FROM customers WHERE organization_id IS NULL;
UPDATE customers SET organization_id=1 WHERE organization_id IS NULL;
ALTER TABLE customers
    MODIFY organization_id BIGINT NOT NULL,
    DROP INDEX email,
    ADD CONSTRAINT fk_customers_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_customers_org_email UNIQUE (organization_id,email),
    ADD INDEX idx_customers_org_name (organization_id,name);

ALTER TABLE document_number_preferences ADD COLUMN organization_id BIGINT NULL AFTER id;
INSERT INTO tenant_data_ownership_audit(table_name,record_id,organization_id,assignment_reason,migration_version)
SELECT 'document_number_preferences',id,1,'Known pre-SaaS rows assigned to the legacy organization created by V54','V57'
FROM document_number_preferences WHERE organization_id IS NULL;
UPDATE document_number_preferences SET organization_id=1 WHERE organization_id IS NULL;
ALTER TABLE document_number_preferences
    MODIFY organization_id BIGINT NOT NULL,
    DROP INDEX uk_document_number_preferences_document_type,
    ADD CONSTRAINT fk_document_number_preferences_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_document_number_preferences_org_type UNIQUE (organization_id,document_type);
