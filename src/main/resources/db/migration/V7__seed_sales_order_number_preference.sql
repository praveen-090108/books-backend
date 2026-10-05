INSERT INTO document_number_preferences
(document_type, auto_generate, prefix, suffix, number_separator, number_format, starting_number, next_number, created_at, updated_at)
VALUES
('orders', 1, 'SO', '', '-', '000000', 153, 153, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE document_type = VALUES(document_type);
