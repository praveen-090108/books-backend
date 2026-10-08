ALTER TABLE e_invoice_details
    MODIFY COLUMN invoice_id BIGINT NULL,
    ADD COLUMN credit_note_id BIGINT NULL AFTER invoice_id,
    ADD CONSTRAINT uk_e_invoice_credit_note UNIQUE (credit_note_id),
    ADD CONSTRAINT fk_e_invoice_credit_note FOREIGN KEY (credit_note_id) REFERENCES business_records (id),
    ADD CONSTRAINT chk_e_invoice_single_document CHECK (
        (invoice_id IS NOT NULL AND credit_note_id IS NULL)
        OR (invoice_id IS NULL AND credit_note_id IS NOT NULL)
    );

CREATE INDEX idx_e_invoice_credit_note ON e_invoice_details (credit_note_id);
