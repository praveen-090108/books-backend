-- Payment ownership is derived from each payment's mandatory primary invoice.
-- The invoice was assigned and audited in V57, providing a reliable ownership link.
ALTER TABLE invoice_payments ADD COLUMN organization_id BIGINT NULL AFTER id;

INSERT INTO tenant_data_ownership_audit(table_name,record_id,organization_id,assignment_reason,migration_version)
SELECT 'invoice_payments',payment.id,invoice.organization_id,
       'Ownership derived from the mandatory primary invoice','V58'
FROM invoice_payments payment
JOIN business_records invoice ON invoice.id=payment.primary_invoice_id
WHERE payment.organization_id IS NULL;

UPDATE invoice_payments payment
JOIN business_records invoice ON invoice.id=payment.primary_invoice_id
SET payment.organization_id=invoice.organization_id
WHERE payment.organization_id IS NULL;

ALTER TABLE invoice_payments
    MODIFY organization_id BIGINT NOT NULL,
    DROP INDEX uk_invoice_payment_number,
    DROP INDEX uk_invoice_payment_idempotency,
    ADD CONSTRAINT fk_invoice_payments_org FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_invoice_payments_org_number UNIQUE (organization_id,payment_number),
    ADD CONSTRAINT uk_invoice_payments_org_idempotency UNIQUE (organization_id,idempotency_key),
    ADD INDEX idx_invoice_payments_org_date (organization_id,payment_date);
