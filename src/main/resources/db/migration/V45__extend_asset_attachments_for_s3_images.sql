ALTER TABLE asset_attachments
    ADD COLUMN storage_key VARCHAR(1000) NULL AFTER file_url,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER attachment_type,
    ADD COLUMN uploaded_by BIGINT NULL AFTER active,
    ADD COLUMN deleted_by BIGINT NULL AFTER uploaded_by,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER deleted_by,
    ADD INDEX idx_asset_attachment_active (asset_id, active, created_at),
    ADD CONSTRAINT fk_asset_attachment_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES app_users(id),
    ADD CONSTRAINT fk_asset_attachment_deleted_by FOREIGN KEY (deleted_by) REFERENCES app_users(id);
