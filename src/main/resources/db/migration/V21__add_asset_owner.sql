ALTER TABLE assets
    ADD COLUMN asset_owner VARCHAR(20) NULL AFTER asset_type;

CREATE INDEX idx_assets_owner
    ON assets (organization_id, asset_owner, deleted);
