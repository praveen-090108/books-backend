ALTER TABLE bank_opening_balance_initializations
    DROP INDEX uk_bank_opening_org_account,
    ADD INDEX idx_bank_opening_org_account (organization_id, bank_account_id);
