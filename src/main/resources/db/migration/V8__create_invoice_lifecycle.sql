CREATE TABLE IF NOT EXISTS invoice_lifecycles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    sent_at DATETIME(6),
    sent_recipient VARCHAR(320),
    first_viewed_at DATETIME(6),
    last_viewed_at DATETIME(6),
    paid_at DATETIME(6),
    voided_at DATETIME(6),
    void_reason VARCHAR(500),
    cash_amount_paid DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    tds_settled DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    credit_applied DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    credit_note_applied DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    balance_due DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_lifecycle_invoice (invoice_id),
    KEY idx_invoice_lifecycle_status (lifecycle_status),
    CONSTRAINT fk_invoice_lifecycle_invoice FOREIGN KEY (invoice_id) REFERENCES business_records (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS invoice_counters (
    counter_key VARCHAR(64) NOT NULL,
    next_value BIGINT NOT NULL,
    PRIMARY KEY (counter_key)
);

INSERT IGNORE INTO invoice_counters (counter_key, next_value) VALUES
('PAYMENT', 1),
('RECEIPT', 1);

CREATE TABLE IF NOT EXISTS invoice_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    primary_invoice_id BIGINT NOT NULL,
    payment_number VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    customer_name VARCHAR(160) NOT NULL,
    payment_date DATE NOT NULL,
    payment_mode VARCHAR(40),
    deposit_account VARCHAR(160),
    reference_number VARCHAR(100),
    gross_amount_received DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    bank_charges DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    net_bank_credit DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    notes TEXT,
    attachment_url VARCHAR(1024),
    send_thank_you_email BOOLEAN NOT NULL DEFAULT FALSE,
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    reversed BOOLEAN NOT NULL DEFAULT FALSE,
    reversed_at DATETIME(6),
    reversal_reason VARCHAR(500),
    created_by VARCHAR(120),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_payment_number (payment_number),
    UNIQUE KEY uk_invoice_payment_idempotency (idempotency_key),
    KEY idx_invoice_payment_invoice (primary_invoice_id),
    CONSTRAINT fk_invoice_payment_invoice FOREIGN KEY (primary_invoice_id) REFERENCES business_records (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS invoice_payment_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    cash_amount_applied DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    tds_amount_applied DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    credit_amount_applied DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_payment_allocation (payment_id, invoice_id),
    KEY idx_payment_allocation_invoice (invoice_id),
    CONSTRAINT fk_payment_allocation_payment FOREIGN KEY (payment_id) REFERENCES invoice_payments (id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_allocation_invoice FOREIGN KEY (invoice_id) REFERENCES business_records (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS invoice_tds_deductions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    base_type VARCHAR(32) NOT NULL,
    base_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    percentage DECIMAL(7,4) NOT NULL DEFAULT 0.0000,
    amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    section_code VARCHAR(80),
    certificate_number VARCHAR(100),
    certificate_date DATE,
    remarks VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_tds_payment (payment_id),
    CONSTRAINT fk_invoice_tds_payment FOREIGN KEY (payment_id) REFERENCES invoice_payments (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payment_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    receipt_number VARCHAR(64) NOT NULL,
    remaining_balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    generated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_receipt_payment (payment_id),
    UNIQUE KEY uk_payment_receipt_number (receipt_number),
    CONSTRAINT fk_payment_receipt_payment FOREIGN KEY (payment_id) REFERENCES invoice_payments (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS invoice_credit_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_reference VARCHAR(100) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    applied_on DATE NOT NULL,
    reversed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_credit_allocation_source (invoice_id, source_type, source_reference),
    KEY idx_credit_allocation_invoice (invoice_id),
    CONSTRAINT fk_credit_allocation_invoice FOREIGN KEY (invoice_id) REFERENCES business_records (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS invoice_credit_note_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    credit_note_id BIGINT NOT NULL,
    amount_applied DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_credit_note_link (invoice_id, credit_note_id),
    CONSTRAINT fk_credit_note_link_invoice FOREIGN KEY (invoice_id) REFERENCES business_records (id) ON DELETE RESTRICT,
    CONSTRAINT fk_credit_note_link_credit_note FOREIGN KEY (credit_note_id) REFERENCES business_records (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS invoice_reminder_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(320),
    message TEXT,
    scheduled_at DATETIME(6),
    sent_at DATETIME(6),
    delivery_status VARCHAR(24) NOT NULL,
    created_by VARCHAR(120),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_invoice_reminder_invoice (invoice_id),
    KEY idx_invoice_reminder_schedule (delivery_status, scheduled_at),
    CONSTRAINT fk_invoice_reminder_invoice FOREIGN KEY (invoice_id) REFERENCES business_records (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS invoice_communication_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    communication_type VARCHAR(24) NOT NULL,
    recipient VARCHAR(320),
    cc VARCHAR(1000),
    bcc VARCHAR(1000),
    subject VARCHAR(500),
    body TEXT,
    scheduled_at DATETIME(6),
    sent_at DATETIME(6),
    delivery_status VARCHAR(24) NOT NULL,
    failure_reason VARCHAR(500),
    created_by VARCHAR(120),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_invoice_communication_invoice (invoice_id),
    KEY idx_invoice_communication_schedule (delivery_status, scheduled_at),
    CONSTRAINT fk_invoice_communication_invoice FOREIGN KEY (invoice_id) REFERENCES business_records (id) ON DELETE CASCADE
);

INSERT IGNORE INTO invoice_lifecycles (
    invoice_id,
    lifecycle_status,
    paid_at,
    cash_amount_paid,
    balance_due
)
SELECT
    id,
    CASE
        WHEN UPPER(REPLACE(status, ' ', '_')) = 'PAID' THEN 'PAID'
        WHEN UPPER(REPLACE(status, ' ', '_')) = 'PARTIALLY_PAID' THEN 'PARTIALLY_PAID'
        WHEN UPPER(REPLACE(status, ' ', '_')) = 'OVERDUE' THEN 'OVERDUE'
        WHEN UPPER(REPLACE(status, ' ', '_')) = 'VOID' THEN 'VOID'
        WHEN UPPER(REPLACE(status, ' ', '_')) = 'SENT' THEN 'SENT'
        WHEN UPPER(REPLACE(status, ' ', '_')) = 'VIEWED' THEN 'VIEWED'
        ELSE 'DRAFT'
    END,
    CASE WHEN UPPER(REPLACE(status, ' ', '_')) = 'PAID' THEN updated_at ELSE NULL END,
    GREATEST(amount - balance_amount, 0.00),
    CASE
        WHEN UPPER(REPLACE(status, ' ', '_')) = 'VOID' THEN 0.00
        ELSE LEAST(GREATEST(balance_amount, 0.00), amount)
    END
FROM business_records
WHERE module = 'sales' AND type = 'invoices';

UPDATE business_records record
JOIN invoice_lifecycles lifecycle ON lifecycle.invoice_id = record.id
SET
    record.status = CASE lifecycle.lifecycle_status
        WHEN 'PARTIALLY_PAID' THEN 'Partially Paid'
        WHEN 'PAID' THEN 'Paid'
        WHEN 'OVERDUE' THEN 'Overdue'
        WHEN 'VOID' THEN 'Void'
        WHEN 'SENT' THEN 'Sent'
        WHEN 'VIEWED' THEN 'Viewed'
        ELSE 'Draft'
    END,
    record.secondary_status = CASE
        WHEN lifecycle.lifecycle_status = 'PAID' THEN 'Paid'
        WHEN lifecycle.lifecycle_status = 'PARTIALLY_PAID' THEN 'Partially Paid'
        ELSE 'Unpaid'
    END;
