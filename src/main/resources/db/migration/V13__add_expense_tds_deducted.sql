ALTER TABLE expenses
    ADD COLUMN tds_deducted DECIMAL(16, 2) NOT NULL DEFAULT 0.00 AFTER total_amount;
