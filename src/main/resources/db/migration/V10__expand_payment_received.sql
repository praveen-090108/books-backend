ALTER TABLE invoice_payments
    ADD COLUMN customer_id BIGINT NULL AFTER primary_invoice_id,
    ADD COLUMN bank_account_name VARCHAR(160) NULL AFTER deposit_account,
    ADD COLUMN bank_name VARCHAR(160) NULL AFTER bank_account_name,
    ADD COLUMN masked_account_number VARCHAR(32) NULL AFTER bank_name,
    ADD COLUMN transaction_id VARCHAR(100) NULL AFTER reference_number,
    ADD COLUMN cheque_number VARCHAR(80) NULL AFTER transaction_id,
    ADD COLUMN cheque_date DATE NULL AFTER cheque_number,
    ADD KEY idx_invoice_payment_customer_date (customer_id, payment_date);

UPDATE invoice_payments payment
JOIN business_records invoice ON invoice.id = payment.primary_invoice_id
SET payment.customer_id = CAST(
    NULLIF(
        JSON_UNQUOTE(JSON_EXTRACT(invoice.notes, '$."Customer ID"')),
        ''
    ) AS UNSIGNED
)
WHERE JSON_VALID(invoice.notes)
  AND JSON_EXTRACT(invoice.notes, '$."Customer ID"') IS NOT NULL;
