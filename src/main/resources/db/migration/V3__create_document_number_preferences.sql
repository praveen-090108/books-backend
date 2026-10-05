CREATE TABLE document_number_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_type VARCHAR(64) NOT NULL,
    auto_generate BIT NOT NULL DEFAULT 1,
    prefix VARCHAR(32),
    suffix VARCHAR(32),
    number_separator VARCHAR(8),
    number_format VARCHAR(32) NOT NULL,
    starting_number BIGINT NOT NULL,
    next_number BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_number_preferences_document_type (document_type)
);

INSERT INTO document_number_preferences
(document_type, auto_generate, prefix, suffix, number_separator, number_format, starting_number, next_number, created_at, updated_at)
VALUES
('quotes', 1, 'QUO', '', '-', '000000', 126, 126, NOW(6), NOW(6)),
('invoices', 1, 'INT-2026', '', '-', '000', 88, 88, NOW(6), NOW(6)),
('creditNotes', 1, 'CN', '', '-', '000', 33, 33, NOW(6), NOW(6));
