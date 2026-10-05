INSERT INTO document_number_preferences
    (document_type, auto_generate, prefix, suffix, number_separator, number_format,
     starting_number, next_number, created_at, updated_at)
SELECT 'customers', 1, 'CUST', '', '-', '0000', 1, 1, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM document_number_preferences WHERE document_type = 'customers'
);
