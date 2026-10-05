CREATE TABLE expense_account_master (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL DEFAULT 1,
    account_name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_by VARCHAR(120) NOT NULL DEFAULT 'System',
    updated_by VARCHAR(120) NOT NULL DEFAULT 'System',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_expense_account_org_name (organization_id, account_name)
);

INSERT IGNORE INTO expense_account_master (organization_id, account_name, display_order) VALUES
(1, 'Advertising and Marketing', 10), (1, 'Bank Fees and Charges', 20),
(1, 'Employee Benefits', 30), (1, 'IT and Internet Expenses', 40),
(1, 'Meals and Entertainment', 50), (1, 'Office Expenses', 60),
(1, 'Professional Fees', 70), (1, 'Rent Expense', 80),
(1, 'Repairs and Maintenance', 90), (1, 'Travel Expense', 100), (1, 'Utilities', 110);

INSERT IGNORE INTO expense_account_master (organization_id, account_name, display_order)
SELECT DISTINCT organization_id, TRIM(expense_account), 1000
FROM expenses
WHERE expense_account IS NOT NULL AND TRIM(expense_account) <> '';

ALTER TABLE expenses ADD COLUMN expense_account_id BIGINT NULL AFTER expense_account;

UPDATE expenses expense
JOIN expense_account_master master
  ON master.organization_id = expense.organization_id
 AND LOWER(master.account_name) = LOWER(TRIM(expense.expense_account))
SET expense.expense_account_id = master.id;

ALTER TABLE expenses
    ADD KEY idx_expense_account_master (expense_account_id),
    ADD CONSTRAINT fk_expense_account_master FOREIGN KEY (expense_account_id) REFERENCES expense_account_master(id) ON DELETE RESTRICT;
