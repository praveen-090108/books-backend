ALTER TABLE resource_employment_history
    ADD COLUMN early_resignation_override BOOLEAN NOT NULL DEFAULT FALSE AFTER remarks;
