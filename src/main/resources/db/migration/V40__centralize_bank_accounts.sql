CREATE TABLE bank_account_master (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL DEFAULT 1,
    account_name VARCHAR(160) NOT NULL,
    bank_name VARCHAR(160) NOT NULL,
    account_holder_name VARCHAR(160),
    account_number VARCHAR(128),
    account_type VARCHAR(40),
    ifsc_code VARCHAR(32),
    branch_name VARCHAR(160),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    opening_balance DECIMAL(19,2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(1000),
    created_by VARCHAR(120) NOT NULL DEFAULT 'System',
    updated_by VARCHAR(120) NOT NULL DEFAULT 'System',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bank_account_org_name (organization_id, account_name)
);

INSERT IGNORE INTO bank_account_master
    (organization_id, account_name, bank_name, account_holder_name, account_number, account_type, currency_code, active)
VALUES
    (1, 'HDFC Current Account', 'HDFC Bank', 'IntelliaTech Solutions Pvt Ltd', '1234', 'CURRENT', 'INR', TRUE),
    (1, 'ICICI Current Account', 'ICICI Bank', 'IntelliaTech Solutions Pvt Ltd', '5678', 'CURRENT', 'INR', TRUE),
    (1, 'SBI Current Account', 'State Bank of India', 'IntelliaTech Solutions Pvt Ltd', '9012', 'CURRENT', 'INR', TRUE);

-- Preserve existing free-text transaction accounts as inactive historical master
-- entries. They remain visible on old transactions but are not offered for new ones.
INSERT IGNORE INTO bank_account_master (organization_id, account_name, bank_name, active)
SELECT DISTINCT organization_id, TRIM(paid_through), TRIM(paid_through), FALSE
FROM expenses WHERE paid_through IS NOT NULL AND TRIM(paid_through) <> '';

INSERT IGNORE INTO bank_account_master (organization_id, account_name, bank_name, active)
SELECT DISTINCT 1, TRIM(paid_through), TRIM(paid_through), FALSE
FROM bill_payments WHERE paid_through IS NOT NULL AND TRIM(paid_through) <> '';

INSERT IGNORE INTO bank_account_master (organization_id, account_name, bank_name, active)
SELECT DISTINCT 1, TRIM(deposit_account), COALESCE(NULLIF(TRIM(bank_name),''), TRIM(deposit_account)), FALSE
FROM invoice_payments WHERE deposit_account IS NOT NULL AND TRIM(deposit_account) <> '';

ALTER TABLE expenses ADD COLUMN bank_account_id BIGINT NULL AFTER paid_through;
ALTER TABLE bill_payments ADD COLUMN bank_account_id BIGINT NULL AFTER paid_through;
ALTER TABLE invoice_payments ADD COLUMN bank_account_id BIGINT NULL AFTER deposit_account;

UPDATE expenses transaction_row
JOIN bank_account_master account
  ON account.organization_id = transaction_row.organization_id
 AND LOWER(account.account_name) = LOWER(TRIM(transaction_row.paid_through))
SET transaction_row.bank_account_id = account.id;

UPDATE bill_payments transaction_row
JOIN bank_account_master account
  ON account.organization_id = 1
 AND LOWER(account.account_name) = LOWER(TRIM(transaction_row.paid_through))
SET transaction_row.bank_account_id = account.id;

UPDATE invoice_payments transaction_row
JOIN bank_account_master account
  ON account.organization_id = 1
 AND LOWER(account.account_name) = LOWER(TRIM(transaction_row.deposit_account))
SET transaction_row.bank_account_id = account.id;

ALTER TABLE expenses
    ADD KEY idx_expense_bank_account (bank_account_id),
    ADD CONSTRAINT fk_expense_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_account_master(id) ON DELETE RESTRICT;
ALTER TABLE bill_payments
    ADD KEY idx_bill_payment_bank_account (bank_account_id),
    ADD CONSTRAINT fk_bill_payment_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_account_master(id) ON DELETE RESTRICT;
ALTER TABLE invoice_payments
    ADD KEY idx_invoice_payment_bank_account (bank_account_id),
    ADD CONSTRAINT fk_invoice_payment_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_account_master(id) ON DELETE RESTRICT;
