ALTER TABLE invoice_payments
    ADD COLUMN currency_code VARCHAR(3) NULL AFTER customer_name;

UPDATE invoice_payments payment
JOIN business_records invoice ON invoice.id = payment.primary_invoice_id
SET payment.currency_code = CASE
    WHEN JSON_VALID(invoice.notes)
         AND JSON_UNQUOTE(JSON_EXTRACT(invoice.notes, '$.Currency')) REGEXP '^[A-Za-z]{3}'
        THEN UPPER(LEFT(JSON_UNQUOTE(JSON_EXTRACT(invoice.notes, '$.Currency')), 3))
    ELSE 'INR'
END
WHERE payment.currency_code IS NULL;

UPDATE invoice_payments SET currency_code = 'INR' WHERE currency_code IS NULL;

ALTER TABLE invoice_payments
    MODIFY COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'INR';
