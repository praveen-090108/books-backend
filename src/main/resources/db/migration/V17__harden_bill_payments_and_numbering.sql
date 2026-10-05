ALTER TABLE bill_payments
    ADD COLUMN idempotency_key VARCHAR(80) NULL AFTER bill_id,
    ADD UNIQUE KEY uk_bill_payments_idempotency (idempotency_key);

INSERT INTO document_number_preferences (
    document_type, auto_generate, prefix, suffix, number_separator, number_format,
    starting_number, next_number, created_at, updated_at
)
SELECT 'bills', TRUE, 'BILL', '', '-', '000000', 1, 1, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM document_number_preferences WHERE document_type = 'bills'
);

INSERT INTO document_number_preferences (
    document_type, auto_generate, prefix, suffix, number_separator, number_format,
    starting_number, next_number, created_at, updated_at
)
SELECT 'billPayments', TRUE, 'PAY-BILL', '', '-', '000000', 1, 1, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM document_number_preferences WHERE document_type = 'billPayments'
);
