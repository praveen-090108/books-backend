ALTER TABLE invoice_payments ADD COLUMN balance_category VARCHAR(24) NULL AFTER bank_account_id;
ALTER TABLE expenses ADD COLUMN balance_category VARCHAR(24) NULL AFTER bank_account_id;
ALTER TABLE bill_payments ADD COLUMN balance_category VARCHAR(24) NULL AFTER bank_account_id;

CREATE TABLE bank_account_ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    bank_account_id BIGINT NOT NULL,
    balance_category VARCHAR(24) NOT NULL,
    transaction_type VARCHAR(40) NOT NULL,
    direction VARCHAR(12) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id BIGINT NULL,
    source_key VARCHAR(120) NOT NULL,
    transaction_date DATE NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    reference_number VARCHAR(120) NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    reversal_of_entry_id BIGINT NULL,
    created_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_bank_ledger_account_date (organization_id, bank_account_id, transaction_date, id),
    INDEX idx_bank_ledger_category (organization_id, balance_category, status),
    INDEX idx_bank_ledger_source (organization_id, source_type, source_key, status),
    CONSTRAINT fk_bank_ledger_account FOREIGN KEY (bank_account_id) REFERENCES bank_account_master(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bank_ledger_reversal FOREIGN KEY (reversal_of_entry_id) REFERENCES bank_account_ledger_entries(id) ON DELETE RESTRICT,
    CONSTRAINT chk_bank_ledger_amount CHECK (amount > 0),
    CONSTRAINT chk_bank_ledger_category CHECK (balance_category IN ('COUNTABLE','NON_COUNTABLE')),
    CONSTRAINT chk_bank_ledger_direction CHECK (direction IN ('IN','OUT')),
    CONSTRAINT chk_bank_ledger_status CHECK (status IN ('POSTED','REVERSED'))
);

CREATE TABLE bank_internal_transfers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    transfer_number VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    from_bank_account_id BIGINT NOT NULL,
    from_category VARCHAR(24) NOT NULL,
    to_bank_account_id BIGINT NOT NULL,
    to_category VARCHAR(24) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    transfer_date DATE NOT NULL,
    reference_number VARCHAR(120) NULL,
    remarks VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'POSTED',
    reversal_reason VARCHAR(1000) NULL,
    created_by VARCHAR(120) NOT NULL,
    posted_by VARCHAR(120) NULL,
    reversed_by VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    posted_at DATETIME(6) NULL,
    reversed_at DATETIME(6) NULL,
    UNIQUE KEY uk_bank_transfer_org_number (organization_id, transfer_number),
    UNIQUE KEY uk_bank_transfer_org_idempotency (organization_id, idempotency_key),
    INDEX idx_bank_transfer_date (organization_id, transfer_date, id),
    CONSTRAINT fk_transfer_from_account FOREIGN KEY (from_bank_account_id) REFERENCES bank_account_master(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_to_account FOREIGN KEY (to_bank_account_id) REFERENCES bank_account_master(id) ON DELETE RESTRICT,
    CONSTRAINT chk_bank_transfer_amount CHECK (amount > 0),
    CONSTRAINT chk_bank_transfer_from_category CHECK (from_category IN ('COUNTABLE','NON_COUNTABLE')),
    CONSTRAINT chk_bank_transfer_to_category CHECK (to_category IN ('COUNTABLE','NON_COUNTABLE')),
    CONSTRAINT chk_bank_transfer_status CHECK (status IN ('POSTED','REVERSED'))
);

CREATE TABLE bank_opening_balance_initializations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    bank_account_id BIGINT NOT NULL,
    opening_date DATE NOT NULL,
    countable_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    non_countable_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    remarks VARCHAR(1000) NULL,
    created_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_bank_opening_org_account (organization_id, bank_account_id),
    CONSTRAINT fk_bank_opening_account FOREIGN KEY (bank_account_id) REFERENCES bank_account_master(id) ON DELETE RESTRICT,
    CONSTRAINT chk_bank_opening_countable CHECK (countable_amount >= 0),
    CONSTRAINT chk_bank_opening_non_countable CHECK (non_countable_amount >= 0)
);

-- Existing bank-linked records remain unclassified by design. Administrators must
-- reconcile them using an approved opening balance or explicitly edit/classify them.
